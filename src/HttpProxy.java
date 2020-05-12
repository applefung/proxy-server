import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class HttpProxy {
    public static String cachePath="";
    public static OutputStream writeCache;
    public static int TIMEOUT=60*1000;//response time out upper bound
    
    @SuppressWarnings("resource")
	public static void main(String[] args) throws IOException {
        ServerSocket serverSocket;
        Socket currsocket=null;
        /** users need to setup work space */

        System.out.println("==============Please enter a name for cache file, enter \"d\" means default cache file=================");
        Scanner scanner=new Scanner(System.in);
        cachePath=scanner.nextLine();
        if(cachePath.equals("d")){
            cachePath="default_cache.txt";
        }
        /** Initialise write_cache */
        writeCache=new FileOutputStream(cachePath,true);
        System.out.println("=================================== Finish cache file setup====================================");

        try {
            //set serversocket，bind port:808
            serverSocket=new ServerSocket(8888);
            int i=0;
            //Loop, grab all the request from this port
            while(true){
                currsocket=serverSocket.accept();
                currsocket.setKeepAlive(true);
                currsocket.setSoTimeout(HttpProxy.TIMEOUT);//set maximum waiting time, if exceeds maximun then disconnect
                //new MyProxy to handle this service
                i++;
                System.out.println("NO. "+i+" thread");
                new MyProxy(currsocket);
            }
        } catch (IOException e) {
            if (currsocket != null) {
                currsocket.close();//close currsocket
            }
            e.printStackTrace();
        }
        writeCache.close();//close writecache
    }
}

