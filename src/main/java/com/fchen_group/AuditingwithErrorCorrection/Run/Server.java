package com.fchen_group.AuditingwithErrorCorrection.Run;

import com.fchen_group.AuditingwithErrorCorrection.main.AuditingwithErrorCorrection;
import com.fchen_group.AuditingwithErrorCorrection.main.ChallengeData;
import com.fchen_group.AuditingwithErrorCorrection.main.ProofData;

import java.io.*;
import java.util.Properties;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Server {
    public static void main(String[] args) throws Exception {
        new Server().run();
    }

    public Server() {
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
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 添加自定义协议的编解码工具
                            ch.pipeline().addLast(new CoolProtocolEncoder());
                            ch.pipeline().addLast(new CoolProtocolDecoder());
                            // 处理网络IO
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 1024) // 设置tcp缓冲区
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            // 绑定端口 同步等待绑定成功
            ChannelFuture f = b.bind(9999).sync();
            // 等到服务端监听端口关闭
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
            File file = new File(new String(coolProtocolReceived.filename));
            file.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(coolProtocolReceived.content);
            fileOutputStream.close();

            switch (coolProtocolReceived.op) {
                case 0:
                    filePathLength = coolProtocolReceived.filename.length;
                case 1:
                    if (filePathLength == 0) {
                        filePathLength = coolProtocolReceived.filename.length - ".properties".length();
                    }
                case 2:
                    if (filePathLength == 0) {
                        filePathLength = coolProtocolReceived.filename.length - ".paritys".length();
                    }

                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(coolProtocolReceived.filename, 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    coolProtocol = new CoolProtocol(coolProtocolReceived.op, filePath.getBytes(), "".getBytes());
                    ctx.writeAndFlush(coolProtocol);
                    break;
                case 3:
                    filePathLength = coolProtocolReceived.filename.length - ".challenge".length();
                    filePathBytes = new byte[filePathLength];
                    System.arraycopy(coolProtocolReceived.filename, 0, filePathBytes, 0, filePathLength);
                    filePath = new String(filePathBytes);
                    prove(filePath);
                    coolProtocol = new CoolProtocol(5, (filePath + ".proof").getBytes(), "".getBytes());
                    ctx.writeAndFlush(coolProtocol);
                    break;
                default:
                    System.out.println("Invalid op");
            }
        }
    }

    public void prove(String filePath) throws Exception {
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
