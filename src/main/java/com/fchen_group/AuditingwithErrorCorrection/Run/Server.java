package com.fchen_group.AuditingwithErrorCorrection.Run;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

public class Server {
    private String pathPrefix;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            new Server(args[0]).run();
        } else {
            System.out.println("show help");
        }
    }

    public Server(String pathPrefix) {
        this.pathPrefix = pathPrefix;
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

            // store file
            String filename = (new File(new String(coolProtocolReceived.filename))).getName();
            File file = new File(pathPrefix + filename);
            file.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(coolProtocolReceived.content);
            fileOutputStream.close();

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
                    prove(filePath);
                    String proofFilePath = pathPrefix + filePath + ".proof";
                    coolProtocol = new CoolProtocol(5, proofFilePath.getBytes());
                    ctx.writeAndFlush(coolProtocol);
                    TimeUnit.SECONDS.sleep(3);
                    (new File(proofFilePath)).delete();
                    break;

                default:
                    System.out.println("Invalid op");
            }
        }
    }

    public void prove(String filePath) throws Exception {
        filePath = pathPrefix + filePath;
        String propertiesFilePath = filePath + ".properties";
        String paritysFilePath = filePath + ".paritys";
        String challengeFilePath = filePath + ".challenge";
        String proofFilePath = filePath + ".proof";

        // get n and k
        FileInputStream propertiesFIS = new FileInputStream(propertiesFilePath);
        Properties properties = new Properties();
        properties.load(propertiesFIS);
        propertiesFIS.close();
        int n = Integer.parseInt(properties.getProperty("n"));
        int k = Integer.parseInt(properties.getProperty("k"));
        // get challenge data
        ChallengeData challengeData = null;
        try {
            File challengeFile = new File(challengeFilePath);
            FileInputStream challengeFIS = new FileInputStream(challengeFile);
            ObjectInputStream in = new ObjectInputStream(challengeFIS);
            challengeData = (ChallengeData) in.readObject();
            in.close();
            challengeFIS.close();
            challengeFile.delete();

            System.out.println("challenge");
            print(challengeData.coefficients);
        } catch (ClassNotFoundException e) {
            System.out.println("Class ChallengeData not found");
            e.printStackTrace();
            return;
        }
        // get paritys
        File paritysFile = new File(paritysFilePath);
        FileInputStream paritysFIS = new FileInputStream(paritysFile);
        AuditingwithErrorCorrection auditingwithErrorCorrection = new AuditingwithErrorCorrection(filePath, n, k);
        int SHARD_NUMBER = auditingwithErrorCorrection.getSHARD_NUMBER(),
                PARITY_SHARDS = auditingwithErrorCorrection.getPARITY_SHARDS();
        System.out.printf("SHARD_NUMBER: %d, PARITY_SHARDS: %d\n", SHARD_NUMBER, PARITY_SHARDS);
        auditingwithErrorCorrection.paritys = new byte[SHARD_NUMBER][PARITY_SHARDS];
        for (int i = 0; i < SHARD_NUMBER; i++) {
            //按行存储，一行相当与一个parity块
            paritysFIS.read(auditingwithErrorCorrection.paritys[i], 0, PARITY_SHARDS);
        }
        paritysFIS.close();

        // calc Proof
        ProofData proof = null;
        proof = auditingwithErrorCorrection.prove(challengeData);

        // store Proof
        FileOutputStream proofFOS = new FileOutputStream(proofFilePath);
        ObjectOutputStream out = new ObjectOutputStream(proofFOS);
        out.writeObject(proof);
        out.close();
        proofFOS.close();
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
