package me.xep.test1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.*;
import java.util.Iterator;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

//https://jungwoon.github.io/java/2019/01/15/NIO-Network.html
public class Client {
    //왜 static -> 쓰레드로 분류하면서 거기서 접근하도록? -> 뭔가 좀
    static Selector selector = null;
    private SocketChannel socketChannel = null;

    public static void main(String args[]) {
        Client client = new Client();
        client.startClient();
    }

    public void initClient() {
        try {
            selector = Selector.open();
            socketChannel = SocketChannel.open(new InetSocketAddress(8081));
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //entry point
    public void startClient() {
        initClient();
        new Thread(new Receiver()).start();
        startWrite();
    }

    private void startWrite(){
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);

        try {
            while (true) {
                Scanner scanner = new Scanner(System.in);
                String msg = scanner.next();
                byteBuffer.clear();
                byteBuffer.put(msg.getBytes(StandardCharsets.UTF_8));
                byteBuffer.flip();
                socketChannel.write(byteBuffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (!Objects.isNull(byteBuffer)) {
                byteBuffer.clear();
            }
        }
    }

    class Receiver implements Runnable {
        private CharsetDecoder charsetDecoder;

        private void read(SelectionKey selectionKey) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);

            try {
                socketChannel.read(byteBuffer);
                byteBuffer.flip();
                //is decoder thread safety? not
                //Instances of this class are not safe for use by multiple concurrent threads.
                String message = charsetDecoder.decode(byteBuffer).toString();
                System.out.println("received: " + message);
                if (!Objects.isNull(byteBuffer)) {
                    byteBuffer.clear();
                }
            } catch (CharacterCodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
             charsetDecoder = StandardCharsets.UTF_8.newDecoder();
             try {
                 while (true) {
                     //이래서 static 으로 선언해 놓긴한것 같은데 조금 깨름직 한데
                     //그냥 생성자로 주입 받는게 낫지 않나

                     //blocking -> 왜 안 블로킹?
                     Client.selector.select();
                     Set<SelectionKey> selectionKeySet = Client.selector.selectedKeys();
                     Iterator<SelectionKey> selectionKeyIterator = selectionKeySet.iterator();

                     while (selectionKeyIterator.hasNext()) {
                        SelectionKey selectionKey = selectionKeyIterator.next();
                        if (selectionKey.isReadable()) {
                            read(selectionKey);
                        }

                        selectionKeyIterator.remove();
                     }
                 }
             } catch (IOException e) {
                 e.printStackTrace();
             }
        }
    }

}
