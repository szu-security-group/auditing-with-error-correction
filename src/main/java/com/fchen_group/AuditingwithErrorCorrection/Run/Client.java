package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.*;
import java.util.Properties;

import com.fchen_group.AuditingwithErrorCorrection.main.Key;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

public class Client {
    private String command;
    private String filePath;
    private int n;
    private int k;
    String propertiesFilePath;
    String keyFilePath;
    String paritysFilePath;
    AuditingwithErrorCorrection auditingwithErrorCorrection;
    private Key key;
    private ChallengeData challengeData;

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
        this(command, filePath, 0, 0);
    }

    public Client(String command, String filePath, int n, int k) {
        this.command = command;
        try {
            this.filePath = (new File(filePath)).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.n = n;
        this.k = k;
        propertiesFilePath = this.filePath + ".properties";
        keyFilePath = this.filePath + ".key";
        paritysFilePath = this.filePath + ".paritys";
        try {
            // initial (n, k)
            if ((new File(propertiesFilePath)).exists()) {
                // get (n, k) from file
                FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
                Properties properties = new Properties();
                properties.load(propertiesFIS);
                propertiesFIS.close();
                this.n = Integer.parseInt(properties.getProperty("n"));
                this.k = Integer.parseInt(properties.getProperty("k"));
            } else {
                // store (n, k) to file
                File propertiesFile = new File(propertiesFilePath);
                propertiesFile.createNewFile();
                FileOutputStream propertiesFOS = new FileOutputStream(propertiesFile);
                Properties properties = new Properties();
                properties.setProperty("n", String.valueOf(this.n));
                properties.setProperty("k", String.valueOf(this.k));
                properties.store(propertiesFOS, "n: the block length of Reed-Solomon codes\n" +
                        "k: the message length of Reed-Solomon codes k");
                propertiesFOS.close();
            }
            // initial auditingwithErrorCorrection
            auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, this.n, this.k);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() throws Exception {
        // 配置客户端NIO线程组
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // 客户端辅助启动类 对客户端配置
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 添加自定义协议的编解码工具
                            ch.pipeline().addLast(new CoolProtocolEncoder());
                            ch.pipeline().addLast(new CoolProtocolDecoder());
                            // 处理客户端操作
                            if (command.equals("outsource"))
                                ch.pipeline().addLast(new ClientOutsourceHandler());
                            else
                                ch.pipeline().addLast(new ClientAuditHandler());
                        }
                    })
                    .option(ChannelOption.TCP_NODELAY, true);

            ChannelFuture f = b.connect("localhost", 9999).sync();
            f.channel().closeFuture().sync();

        } finally {
            group.shutdownGracefully();
        }
    }

    class ClientOutsourceHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // keyGen
            key = auditingwithErrorCorrection.keyGen();
            // store key
            File keyFile = new File(keyFilePath);
            if (!keyFile.exists())
                keyFile.createNewFile();
            FileOutputStream keyFOS = new FileOutputStream(keyFile);
            Properties keyProperties = new Properties();
            keyProperties.setProperty("k", key.k);
            keyProperties.setProperty("s", key.s);
            keyProperties.store(keyFOS, "key = (Key, sKey)");
            keyFOS.close();

            // outsource
            byte[][] paritys = auditingwithErrorCorrection.outsource(key);
            // store paritys
            File paritysFile = new File(paritysFilePath);
            if (!paritysFile.exists())
                paritysFile.createNewFile();
            FileOutputStream paritysFOS = new FileOutputStream(paritysFile);
            for (byte[] parity : paritys) {
                //按行存储，一行相当与一个 parity 块
                paritysFOS.write(parity);
            }
            paritysFOS.close();

            // send file
            CoolProtocol coolProtocol = new CoolProtocol(0, filePath.getBytes());
            ctx.writeAndFlush(coolProtocol);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) {
            switch (((CoolProtocol) msg).op) {
                case 0:
                    ctx.writeAndFlush(new CoolProtocol(1, propertiesFilePath.getBytes()));
                    break;
                case 1:
                    ctx.writeAndFlush(new CoolProtocol(2, paritysFilePath.getBytes()));
                    break;
                case 2:
                    ctx.close();
                    break;
            }
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
            // get key
            FileInputStream keyFIS = new FileInputStream(keyFilePath);
            Properties keyProperties = new Properties();
            keyProperties.load(keyFIS);
            keyFIS.close();
            key = new Key(keyProperties.getProperty("k"), keyProperties.getProperty("s"));

            challengeData = auditingwithErrorCorrection.audit(key);
            CoolProtocol coolProtocol = new CoolProtocol(3, (filePath + ".challenge").getBytes(), serialize(challengeData));
            ctx.writeAndFlush(coolProtocol);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            // receive proof data
            CoolProtocol coolProtocolReceived = (CoolProtocol) msg;
            ProofData proofData = (ProofData) deserialize(coolProtocolReceived.content);
            ctx.close();
            boolean verifyResult = auditingwithErrorCorrection.verify(key, challengeData, proofData);
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

    public static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(object);
        return byteArrayOutputStream.toByteArray();
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        return objectInputStream.readObject();
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
