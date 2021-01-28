import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        int bindPort = 8881;
        InetSocketAddress destAddr = new InetSocketAddress("127.0.0.1", 8882);

        System.out.println("Listen on: " + bindPort + " port");
        new ServerBootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        socketChannel.config().setAutoRead(false);
                        socketChannel.pipeline()
                                .addLast(new ServerHandle(destAddr));
                    }
                })
                .bind(bindPort)
                .sync();
    }

    static class ServerHandle extends ChannelInboundHandlerAdapter {

        private Channel destChannel = null;
        private final SocketAddress destAddr;

        public ServerHandle(SocketAddress destAddr) {
            this.destAddr = destAddr;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            socketChannel.pipeline()
                                    .addLast(new ClientHandle(ctx.channel()));
                        }
                    })
                    .connect(destAddr)
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            destChannel = future.channel();
                            ctx.channel().config().setAutoRead(destChannel.isWritable());
                        } else {
                            ctx.close();
                        }
                    });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (destChannel != null && destChannel.isActive()) {
                destChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        future.channel().close();
                        ctx.close();
                    }
                });
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (destChannel != null && destChannel.isActive()) {
                destChannel.close();
            }
        }
    }

    static class ClientHandle extends ChannelInboundHandlerAdapter {

        private final Channel sourceChannel;

        public ClientHandle(Channel sourceChannel) {
            this.sourceChannel = sourceChannel;
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            sourceChannel.config().setAutoRead(ctx.channel().isWritable());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            sourceChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    future.channel().close();
                    ctx.close();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            sourceChannel.close();
        }
    }
}
