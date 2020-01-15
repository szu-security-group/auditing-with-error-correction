package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

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
            String propertiesFilePath = filePath + ".properties";
            String keyFilePath = filePath + ".key";
            String paritysFilePath = filePath + ".paritys";

            // parse args (n, k) and store them to
            File propertiesFile = new File(propertiesFilePath);
            propertiesFile.createNewFile();
            FileOutputStream propertiesFOS = new FileOutputStream(propertiesFile);
            Properties properties = new Properties();
            properties.setProperty("n", String.valueOf(n));
            properties.setProperty("k", String.valueOf(k));
            properties.store(propertiesFOS, "n: the block length of Reed-Solomon codes\n" +
                    "k: the message length of Reed-Solomon codes k");
            propertiesFOS.close();

            // keyGen
            AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
            Key key = auditingwithErrorCorrection.keyGen();
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
            for (int i = 0; i < paritys.length; i++) {
                //按行存储，一行相当与一个 parity 块
                paritysFOS.write(paritys[i]);
            }
            paritysFOS.close();

            // send file
            CoolProtocol coolProtocol = new CoolProtocol(0, filePath.getBytes());
            ctx.writeAndFlush(coolProtocol);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            String propertiesFilePath = filePath + ".properties";
            String paritysFilePath = filePath + ".paritys";
            CoolProtocol coolProtocolReceived = (CoolProtocol) msg;

            if (coolProtocolReceived.op == 0) {
                CoolProtocol coolProtocol = new CoolProtocol(1, propertiesFilePath.getBytes());
                ctx.writeAndFlush(coolProtocol);
            } else if (coolProtocolReceived.op == 1) {
                CoolProtocol coolProtocol = new CoolProtocol(2, paritysFilePath.getBytes());
                ctx.writeAndFlush(coolProtocol);
            } else if (coolProtocolReceived.op == 2) {
                ctx.close();
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
            String propertiesFilePath = filePath + ".properties";
            String challengeFilePath = filePath + ".challenge";
            String keyFilePath = filePath + ".key";

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
            Properties keyProperties = new Properties();
            keyProperties.load(keyFIS);
            keyFIS.close();
            Key key = new Key(keyProperties.getProperty("k"), keyProperties.getProperty("s"));

            // audit
            ChallengeData challengeData = auditingwithErrorCorrection.audit(key);

            System.out.println("challenge");
            print(challengeData.coefficients);

            // store challenge data
            FileOutputStream challengeFOS = new FileOutputStream(challengeFilePath);
            ObjectOutputStream out = new ObjectOutputStream(challengeFOS);
            out.writeObject(challengeData);
            out.close();
            challengeFOS.close();

            CoolProtocol coolProtocol = new CoolProtocol(3, challengeFilePath.getBytes());
            ctx.writeAndFlush(coolProtocol);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            // receive proof file
            CoolProtocol coolProtocolReceived = (CoolProtocol) msg;
            String receivedFilename = (new File(new String(coolProtocolReceived.filename))).getName();
            File file = new File(filePath + ".proof");
            file.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(coolProtocolReceived.content);
            fileOutputStream.close();
            ctx.close();

            String propertiesFilePath = filePath + ".properties";
            String challengeFilePath = filePath + ".challenge";
            String keyFilePath = filePath + ".key";
            String proofFilePath = filePath + ".proof";

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
            Properties keyProperties = new Properties();
            keyProperties.load(keyFIS);
            keyFIS.close();
            Key key = new Key(keyProperties.getProperty("k"), keyProperties.getProperty("s"));

            // get challenge data
            ChallengeData challengeData = null;
            try {
                File challengeFile = new File(challengeFilePath);
                FileInputStream challengeFIS = new FileInputStream(challengeFile);
                ObjectInputStream in = new ObjectInputStream(challengeFIS);
                challengeData = (ChallengeData) in.readObject();
                in.close();
                challengeFIS.close();
                TimeUnit.SECONDS.sleep(3);
                challengeFile.delete();

                System.out.println("challenge");
                print(challengeData.coefficients);
            } catch (ClassNotFoundException e) {
                System.out.println("Class ChallengeData not found");
                e.printStackTrace();
                return;
            }

            // get proof data
            ProofData proofData = null;
            try {
                FileInputStream proofFIS = new FileInputStream(proofFilePath);
                ObjectInputStream in = new ObjectInputStream(proofFIS);
                proofData = (ProofData) in.readObject();
                in.close();
                proofFIS.close();
                (new File(proofFilePath)).delete();

                System.out.println("proof");
                print(proofData.dataproof);
                print(proofData.parityproof);
            } catch (ClassNotFoundException e) {
                System.out.println("Class ChallengeData not found");
                e.printStackTrace();
                return;
            }

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
