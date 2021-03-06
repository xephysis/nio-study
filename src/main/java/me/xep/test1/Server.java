package me.xep.test1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;

//https://jungwoon.github.io/java/2019/01/15/NIO-Network.html
//버그가 많긴 함 -> 이걸 쓸 건 아니니까 나중에 심심하면 고쳐보는걸로 일단은 셀럭터 멀티플렉싱을 이해하는게 우선이었으니
public class Server {

    private Selector selector = null;
    private Vector<SocketChannel> clientSocketChannels = new Vector();
    private CharsetDecoder decoder = null;

    public static void main(String args[]) {
        Server server = new Server();
        server.initServer();
        server.startServer();
    }

    public void initServer() {
        try {
            decoder = StandardCharsets.UTF_8.newDecoder();

            selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(8081));

            //서버 소켓 채널을 셀렉터에 등록해둠
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startServer() {
        System.out.println("start server");

        while (true) {
            try {
                //blocking
                var keyCount = selector.select();

                if (keyCount == 0) {
                    continue;
                }

                //not thread safe
                Set<SelectionKey> selectionKeySet = selector.selectedKeys();
                Iterator<SelectionKey> selectionKeyIterator = selectionKeySet.iterator();

                while (selectionKeyIterator.hasNext()) {
                    SelectionKey selectionKey = selectionKeyIterator.next();

                    if (selectionKey.isAcceptable()) {
                        accept(selectionKey);
                    } else if (selectionKey.isReadable()) {
                        read(selectionKey);
                    }
                    //이 작업은 왜 필요한거지 어짜피 한번만 순회할텐데
                    selectionKeyIterator.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //accept 대기 상태인 key 를 받아다가 key 의 채널을 추출한 뒤, accept 를 수행하고 OP_READ로 등록
    private void accept(SelectionKey selectionKey) {
        //type safety 를 보장할 수 없나
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();

        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            if (Objects.isNull(socketChannel)) {
                return;
            }

            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);

            clientSocketChannels.add(socketChannel);

            System.out.println(socketChannel.toString() + ": client connected");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //read 대기 상태인 key 를 받아다가 direct buffer 를 만들어서 read 한 뒤, broadcast
    private void read(SelectionKey selectionKey) {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);

        try {
            socketChannel.read(byteBuffer);
        } catch (IOException e) {
            try {
                socketChannel.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

            clientSocketChannels.remove(socketChannel);
            e.printStackTrace();
        }
        broadcast(byteBuffer);
        byteBuffer.clear();
    }

    private void broadcast(ByteBuffer byteBuffer) {
        //바이트 버퍼 다 채워졌으니 사용하지 전에 flip 해서 limit 을 당겨둠
        byteBuffer.flip();
        try {
            System.out.println("broadcast: " + decoder.decode(byteBuffer));
        } catch (CharacterCodingException e) {
            //do nothing
            e.printStackTrace();
        }
        //byte buffer 를 매번 재활용 하는것보다 그냥 String 으로 만들어 놓고 쓰는게 더 나을것 같음
        byteBuffer.rewind();

        Iterator<SocketChannel> clientsIterator = clientSocketChannels.iterator();

        while (clientsIterator.hasNext()) {
            SocketChannel socketChannel = clientsIterator.next();

            if (!Objects.isNull(socketChannel)
                    //두 메소드 차이는 뭐지 -> isConnected 안에 isOpen 포함
                    && socketChannel.isConnected()) {
                try {
                    socketChannel.write(byteBuffer);
                } catch (IOException e) {
                    clientSocketChannels.remove(socketChannel);
                    e.printStackTrace();
                } finally {
                    byteBuffer.rewind();
                }
            }
        }
    }

}
