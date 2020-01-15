package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.*;
import java.util.Properties;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.model.PutObjectResult;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

public class Server {
    private String pathPrefix;
    private String COSConfigFilePath;

    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            new Server(args[0], args[1]).run();
        } else {
            System.out.println("show help");
        }
    }

    public Server(String pathPrefix, String COSConfigFilePath) {
        this.pathPrefix = pathPrefix;
        this.COSConfigFilePath = COSConfigFilePath;
    }

    public void run() throws Exception {
        // 配置NIO线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            // 服务器辅助启动类配置
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 添加自定义协议的编解码工具
                            ch.pipeline().addLast(new CoolProtocolEncoder());
                            ch.pipeline().addLast(new CoolProtocolDecoder());
                            // 处理服务器端操作
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1024) // 设置tcp缓冲区
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(9999).sync();
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    class ServerHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            int filePathLength = 0;
            byte[] filePathBytes;
            String filePath = "";
            CoolProtocol coolProtocol;
            CoolProtocol coolProtocolReceived = (CoolProtocol) msg;

            String filename = (new File(new String(coolProtocolReceived.filename))).getName();

            switch (coolProtocolReceived.op) {
                case 0:
                    filePathLength = filename.length();
                case 1:
                    if (filePathLength == 0) {
                        filePathLength = filename.length() - ".properties".length();
                    }
                case 2:
                    if (filePathLength == 0) {
                        filePathLength = filename.length() - ".paritys".length();
                    }

                    // store file
                    File file = new File(pathPrefix + filename);
                    file.createNewFile();
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    fileOutputStream.write(coolProtocolReceived.content);
                    fileOutputStream.close();
                    // uploadFile(pathPrefix + filename, filename);

                    // send confirm
                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(filename.getBytes(), 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    coolProtocol = new CoolProtocol(coolProtocolReceived.op, filePath.getBytes(), "".getBytes());
                    ctx.writeAndFlush(coolProtocol);
                    break;

                case 3:
                    filePathLength = filename.length() - ".challenge".length();
                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(filename.getBytes(), 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    ChallengeData challengeData = (ChallengeData) deserialize(coolProtocolReceived.content);
                    ProofData proofData = prove(filePath, challengeData);
                    String proofFilePath = pathPrefix + filePath + ".proof";
                    coolProtocol = new CoolProtocol(5, proofFilePath.getBytes(), serialize(proofData));
                    ctx.writeAndFlush(coolProtocol);
                    break;

                default:
                    System.out.println("Invalid op");
            }
        }
    }

    public void uploadFile(String localFileName, String cloudFileName) {
        // 从配置文件获取 secretId, secretKey
        String secretId = "";
        String secretKey = "";
        try {
            FileInputStream propertiesFIS = new FileInputStream(COSConfigFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();
            secretId = properties.getProperty("secretId");
            secretKey = properties.getProperty("secretKey");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 初始化用户身份信息（secretId, secretKey）。
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 设置 bucket 的区域
        Region region = new Region("ap-chengdu");
        ClientConfig clientConfig = new ClientConfig(region);
        // 生成 cos 客户端。
        COSClient cosClient = new COSClient(cred, clientConfig);
        // 设置 BucketName-APPID
        String bucketName = "crypto2019-1254094112";

        File localFile = new File(localFileName);
        PutObjectResult putObjectResult = cosClient.putObject(bucketName, cloudFileName, localFile);
    }

    public ProofData prove(String filePath, ChallengeData challengeData) throws Exception {
        String filename = filePath;
        filePath = pathPrefix + filename;
        String propertiesFilePath = filePath + ".properties";
        String paritysFilePath = filePath + ".paritys";

        // get n and k
        FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
        Properties properties = new Properties();
        properties.load(propertiesFIS);
        propertiesFIS.close();
        int n = Integer.parseInt(properties.getProperty("n"));
        int k = Integer.parseInt(properties.getProperty("k"));
        // get paritys
        File paritysFile = new File(paritysFilePath);
        FileInputStream paritysFIS = new FileInputStream(paritysFile);
        AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
        int SHARD_NUMBER = auditingwithErrorCorrection.getSHARD_NUMBER(),
                PARITY_SHARDS = auditingwithErrorCorrection.getPARITY_SHARDS();
        System.out.printf("SHARD_NUMBER: %d, PARITY_SHARDS: %d\n", SHARD_NUMBER, PARITY_SHARDS);
        byte[][] paritys = new byte[SHARD_NUMBER][PARITY_SHARDS];
        for (int i = 0; i < SHARD_NUMBER; i++) {
            //按行存储，一行相当与一个parity块
            paritysFIS.read(paritys[i], 0, PARITY_SHARDS);
        }
        paritysFIS.close();

        // calc Proof and return
        return auditingwithErrorCorrection.prove(paritys, challengeData, COSConfigFilePath, filename);
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
        if (data == null)
            return;
        for (int i = 0; i < 10; i++) {
            System.out.print(String.format("%02x ", data[0][i]));
        }
        System.out.println();
    }
}
