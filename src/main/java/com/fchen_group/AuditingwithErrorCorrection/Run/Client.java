package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.*;
import java.util.Properties;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import com.javamex.classmexer.MemoryUtil;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

public class Client {
    private String command;
    private String filePath;
    private int n;
    private int k;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("audit")) {
            new Client(args[0], args[1]).run();
        } else if (args.length == 4 && args[0].equals("outsource")) {
            new Client(args[0], args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3])).run();
        } else {
            System.out.println("show help");
        }
    }

    public Client(String command, String filePath) {
        this.command = command;
        this.filePath = filePath;
    }

    public Client(String command, String filePath, int n, int k) {
        this.command = command;
        this.filePath = filePath;
        this.n = n;
        this.k = k;
    }

    public void run() throws Exception {
        // 配置客户端NIO线程组
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // 客户端辅助启动类 对客户端配置
            Bootstrap b = new Bootstrap();
            b.group(group)//
                    .channel(NioSocketChannel.class)//
                    .option(ChannelOption.TCP_NODELAY, true)//
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 添加自定义协议的编解码工具
                            ch.pipeline().addLast(new CoolProtocolEncoder());
                            ch.pipeline().addLast(new CoolProtocolDecoder());
                            // 处理网络IO
                            if (command.equals("outsource"))
                                ch.pipeline().addLast(new ClientOutsourceHandler());
                            else
                                ch.pipeline().addLast(new ClientAuditHandler());
                        }
                    });//
            // 异步链接服务器 同步等待链接成功
            ChannelFuture f = b.connect("localhost", 9999).sync();

            // 等待链接关闭
            f.channel().closeFuture().sync();

        } finally {
            group.shutdownGracefully();
        }
    }

    class ClientOutsourceHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            String propertiesFilePath = filePath + ".properties";
            String keyFilePath = filePath + ".key";
            String paritysFilePath = filePath + ".paritys";

            // parse args (n, k) and write them to properties
            File propertiesFile = new File(propertiesFilePath);
            propertiesFile.createNewFile();
            FileOutputStream propertiesFOS = new FileOutputStream(propertiesFile);
            Properties properties = new Properties();
            properties.setProperty("n", String.valueOf(n));
            properties.setProperty("k", String.valueOf(k));
            properties.store(propertiesFOS, "n: the block length of Reed-Solomon codes\n" +
                    "k: the message length of Reed-Solomon codes k");
            propertiesFOS.close();

            // outsource
            AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
            auditingwithErrorCorrection.keyGen();
            auditingwithErrorCorrection.outsource();

            // store key
            File keyFile = new File(keyFilePath);
            if (!keyFile.exists())
                keyFile.createNewFile();
            FileOutputStream keyFOS = new FileOutputStream(keyFile);
            Properties key = new Properties();
            key.setProperty("Key", auditingwithErrorCorrection.getKey());
            key.setProperty("sKey", auditingwithErrorCorrection.getsKey());
            key.store(keyFOS, "key = (Key, sKey)");
            keyFOS.close();

            System.out.println("outsource");
            print(auditingwithErrorCorrection.paritys);

            // write paritys to file
            File paritysFile = new File(paritysFilePath);
            if (!paritysFile.exists())
                paritysFile.createNewFile();
            FileOutputStream paritysFOS = new FileOutputStream(paritysFile);
            for (int i = 0; i < auditingwithErrorCorrection.paritys.length; i++) {
                //按行存储，一行相当与一个parity块
                paritysFOS.write(auditingwithErrorCorrection.paritys[i]);
            }
            paritysFOS.close();

            // send file
            CoolProtocol coolProtocol = new CoolProtocol(filePath.getBytes(), "".getBytes());
            ctx.writeAndFlush(coolProtocol);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            // receive message example
            // CoolProtocol body = (CoolProtocol) msg;
            // System.out.println("Client接受的客户端的信息 :" + body.toString());
            // ctx.close();
            CoolProtocol coolProtocolReceived = (CoolProtocol) msg;
            CoolProtocol coolProtocol = new CoolProtocol(coolProtocolReceived.filename, "".getBytes());
            ctx.writeAndFlush(coolProtocol);
            if ((new String(coolProtocol.filename)).equals(filePath + ".paritys"))
                ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    class ClientAuditHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            String propertiesFilePath = filePath + ".properties";
            String challengeFilePath = filePath + ".challenge";

            FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            int n = Integer.parseInt(properties.getProperty("n"));
            int k = Integer.parseInt(properties.getProperty("k"));

            int challengeLen = 460;
            AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
            ChallengeData challengeData = auditingwithErrorCorrection.audit(challengeLen);

            System.out.println("challenge");
            print(challengeData.coefficients);

            // store challenge data
            FileOutputStream challengeFOS = new FileOutputStream(challengeFilePath);
            ObjectOutputStream out = new ObjectOutputStream(challengeFOS);
            out.writeObject(challengeData);
            out.close();
            challengeFOS.close();

            CoolProtocol coolProtocol = new CoolProtocol(challengeFilePath.getBytes(), "".getBytes());
            ctx.writeAndFlush(coolProtocol);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            // receive proof file
            CoolProtocol coolProtocolReceived = (CoolProtocol) msg;
            File file = new File(new String(coolProtocolReceived.filename));
            file.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(coolProtocolReceived.content);
            fileOutputStream.close();
            ctx.close();

            String propertiesFilePath = filePath + ".properties";
            String challengeFilePath = filePath + ".challenge";
            String keyFilePath = filePath + ".key";
            String proofFilePath = new String(coolProtocolReceived.filename);

            boolean verifyResult = false;

            // get n and k
            FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            int n = Integer.parseInt(properties.getProperty("n"));
            int k = Integer.parseInt(properties.getProperty("k"));

            // get key
            AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
            FileInputStream keyFIS = new FileInputStream(keyFilePath);
            Properties key = new Properties();
            key.load(keyFIS);
            keyFIS.close();
            String Key = key.getProperty("Key");
            String sKey = key.getProperty("sKey");
            auditingwithErrorCorrection.setKey(Key);
            auditingwithErrorCorrection.setsKey(sKey);

            // get challenge data
            ChallengeData challengeData = null;
            try {
                FileInputStream challengeFIS = new FileInputStream(challengeFilePath);
                ObjectInputStream in = new ObjectInputStream(challengeFIS);
                challengeData = (ChallengeData) in.readObject();
                in.close();
                challengeFIS.close();

                System.out.println("challenge");
                print(challengeData.coefficients);
            } catch (ClassNotFoundException e) {
                System.out.println("Class ChallengeData not found");
                e.printStackTrace();
                return;
            }

            // get proof data
            ProofData proof = null;
            try {
                FileInputStream proofFIS = new FileInputStream(proofFilePath);
                ObjectInputStream in = new ObjectInputStream(proofFIS);
                proof = (ProofData) in.readObject();
                in.close();
                proofFIS.close();

                System.out.println("proof");
                print(proof.dataproof);
                print(proof.parityproof);
            } catch (ClassNotFoundException e) {
                System.out.println("Class ChallengeData not found");
                e.printStackTrace();
                return;
            }

            verifyResult = auditingwithErrorCorrection.verify(challengeData, proof);
            if (verifyResult)
                System.out.println("Verify pass");
            else
                System.out.println("Verify failed");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    public static void print(byte[] data) {
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[i]));
        }
        System.out.println();
    }

    public static void print(byte[][] data) {
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[0][i]));
        }
        System.out.println();
    }
}
