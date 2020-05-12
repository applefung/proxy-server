import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class MyProxy extends Thread {

    Socket socket;
    String targetHost=null;
    Integer targetPort = 80;
    InputStream cin;//read request from browser
    OutputStream cout;//send data to browser
    PrintWriter outPrintWriter_client;//write data to browser
    BufferedReader bufferedReader_client;//buffer request from browser

    Socket proxySocket;//this socket is socket for connecting to website

    PrintWriter outPrintWriter_Web;//send request to website
    BufferedReader bufferedReader_web;//buffer request from website

    String cacheFilePath;
    File file=null;
    FileInputStream fileInputStream;
    String url="";
    ArrayList<String>cache;
    int cache_url_index=-1;
    boolean has_cache_no_timestamp=false;

    public MyProxy(Socket inputSocket) throws IOException {
        socket=inputSocket;
        /** Create a file */
        file=new File(HttpProxy.cachePath);
        if (!file.exists()){//if the file does not exist then create a new one
            file.createNewFile();
        }

        fileInputStream=new FileInputStream(HttpProxy.cachePath);

        System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"proxy server start");
        System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"acquired socket is from"+inputSocket.getInetAddress()+":"+inputSocket.getPort());

        cin=socket.getInputStream();//Create inputStream from browser
        bufferedReader_client=new BufferedReader(new InputStreamReader(cin));
        cout=socket.getOutputStream();//Create outputStrem to browser
        outPrintWriter_client=new PrintWriter(cout);
        /** read cache */
        cache=readCache(fileInputStream);
        System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"There are "+cache.size()+" lines in cache file\r\n");

        start();//start a thread
    }
    public void run() {
        String line = "";
        String firstline = "";
        String tempHost = "";
        String type = null;   //type: http/https
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(cin));//buffer inputStream
            int temp = 1;
            StringBuilder sb = new StringBuilder();
        	while (true)
        	{
                try
                {
                    if ((line = br.readLine()) == null)
                        break;
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("\nrequest: "+line);

                if (temp == 1)
                {

                    /** get url */
                    url=getURL(line);
                    firstline = line;

                    
                    //get request type to judge http or https
                    type = line.split(" ")[0];
                    if (type == null)
                        continue;
                }
                temp++;
                String[] s1 = line.split(": ");
                if (line.isEmpty()) {
                    break;
                }
                for (int i = 0; i < s1.length; i++) {
                    if (s1[i].equalsIgnoreCase("host")) {
                        tempHost = s1[i + 1];
                    }
                }
                sb.append(line + "\r\n");
                


                /** write request into cache file, if there exist the duplicated cache, do not write */
                boolean has_in_cache_already=false;
                for(String iter:cache){
                    if (iter.equals(line)) {
                        has_in_cache_already = true;
                        break;
                    }
                }
                if (has_in_cache_already==false){
                    String tempStr = line + "\r\n";
                    WriteCache.write_cache(tempStr.getBytes(), 0, tempStr.length());
                }
                
                line = null;
            }
            sb.append("\r\n");          //if do not add this line, cannot continue --> tell server the request is finished
            WriteCache.write_cache("\r\n".getBytes(), 0, "\r\n".length());

            System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"read header from browser.... \r\n"+sb.toString());



            /** get host and port */
            if (tempHost.split(":").length > 1) {
            	targetPort = Integer.valueOf(tempHost.split(":")[1]);
            	System.out.println("targetPort: "+targetPort);
            }
            targetHost = tempHost.split(":")[0];

            System.out.println("[Thread-"+Thread.currentThread().getId()+"] connection type:" + type + " host name:" + targetHost + " port: " + targetPort);

            if (targetPort != null && !targetHost.equals("")) {
                try {
                    /** try to connect to the target host */
                	proxySocket = new Socket(targetHost, targetPort);
	                //debug
	                System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+" request from targetHost:" + targetHost);
                    proxySocket.setSoTimeout(HttpProxy.TIMEOUT);//set timeout for connecting proxy server and target server
                    InputStream proxyIs = proxySocket.getInputStream();
                    OutputStream proxyOs = proxySocket.getOutputStream();
	                /** if the cache file is not empty */
                    boolean goahead_call = false;
	                if (cache.size()!=0) {//if the cache file is not empty, find request
	                    String modifyTime;
	                    String info="";
	                    modifyTime=findModifyTime(cache, firstline);//get modifytime
	                    if (modifyTime!=null||has_cache_no_timestamp){
	                    	bufferedReader_web = new BufferedReader(new InputStreamReader(proxyIs));
	                        outPrintWriter_Web = new PrintWriter(proxySocket.getOutputStream());//ready to send request to browser

	                        /** if the cache does not contain last-modified, then no need to query for if-modify, otherwise query for if-momdify */
	                        if (!has_cache_no_timestamp){
	                            firstline += "\r\n";
	                            outPrintWriter_Web.write(firstline);
	                            System.out.print("send modify time request:\n" + firstline);
	                            String str1 = "Host: " + targetHost + "\r\n";
	                            outPrintWriter_Web.write(str1);
	                            String str = "If-modified-since: " + modifyTime
	                                    + "\r\n";
	                            outPrintWriter_Web.write(str);
	                            outPrintWriter_Web.write("\r\n");
	                            outPrintWriter_Web.flush();
	                            System.out.print(str1);
	                            System.out.print(str);

	                            info= bufferedReader_web.readLine();
	                            System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"the message from server：" + info);
	                        }

	                        if (info.contains("Not Modified")||has_cache_no_timestamp) {//if the server responds with 304 Not Modified, send cache data to browser
	                            String temp_response="";
	                            System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"use cache data------");
	                            if (cache_url_index!=-1)
	                                for (int i=cache_url_index+1;i<cache.size();i++){
	                                    if (cache.get(i).contains("http://"))
	                                        break;
	                                    temp_response+=cache.get(i);
	                                    temp_response+="\r\n";

	                                }
	                            System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"use cache：\n"+temp_response);
	                            cout.write(temp_response.getBytes(),0,temp_response.getBytes().length);
	                            cout.write("\r\n".getBytes(),0,"\r\n".getBytes().length);
	                            cout.flush();
	                        } else {
	                            /** if the server does not respond, with 304 Not Modified, send request to server */
	                        	goahead_call = true;
	                        }
	                    } else {
                            goahead_call = true;
                        }
	
	                } else {
	                	goahead_call = true;
	                }
                    if(goahead_call){
	                    if (type.equalsIgnoreCase("connect")) {
	                        //https, tell server the connection is establlished
	                        cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
	                        cout.flush();
	                    } else {//http request
	                        proxyOs.write(sb.toString().getBytes("UTF-8"));
	                        proxyOs.flush();
	                    }
	                    new ProxyHandleThread(cin,proxyOs).start();
	                    //listen the message from client and send to the server
	                    new ProxyHandleThread(proxyIs, cout).start();
	                    //listen the message from server and send to the client
                    }
	            } catch (Exception e) {
                    System.out.println("404 Not Found");
	                e.printStackTrace();
	            }
	        }
        }catch (Exception e){
            e.printStackTrace();
        }
    }



    private String getURL(String firstline){
        StringTokenizer stringTokenizer=new StringTokenizer(firstline);
        stringTokenizer.nextToken();
        return stringTokenizer.nextToken();
    }



    private ArrayList<String> readCache(FileInputStream fileInputStream){
        ArrayList<String> result=new ArrayList<>();
        String temp;
        BufferedReader br=new BufferedReader(new InputStreamReader(fileInputStream));
        try {
            while((temp=br.readLine())!=null){
                result.add(temp);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    private String findModifyTime(ArrayList<String> cache_temp, String request){
        String LastModifiTime=null;
        int startSearching=0;
        has_cache_no_timestamp=false;
        System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"compare url: "+request);
        for(int i=0;i<cache_temp.size();i++){

            if (cache_temp.get(i).equals(request)){
                startSearching=i;
                cache_url_index=i;
                for(int j=startSearching+1;j<cache_temp.size();j++){
                    if(cache_temp.get(j).contains("http://"))
                        break;
                    if (cache_temp.get(j).contains("Last-Modified:")){
                        LastModifiTime=cacheFilePath.substring(cache_temp.get(j).indexOf("Last-Modified:"));
                        System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"modifytime："+LastModifiTime);
                        return LastModifiTime;
                    }
                    if (cache_temp.get(j).contains("<html>")){
                        has_cache_no_timestamp=true;
                        System.out.println("[Thread-"+Thread.currentThread().getId()+"] "+"modifytime："+LastModifiTime);
                        return LastModifiTime;
                    }
                }
            }
        }

        return LastModifiTime;
    }


    class ProxyHandleThread extends Thread
    {
        //use to resend the message
        private InputStream Input;
        private OutputStream Output;

        public ProxyHandleThread(InputStream Input, OutputStream Output)
        {
            this.Input = Input;
            this.Output= Output;
        }

        @Override
        public void run()
        {
            BufferedInputStream clientbis = new BufferedInputStream(Input);
            byte[] bytes =new byte[4096];
            int length=-1;

            try {
                while (true) {

                    while((length=clientbis.read(bytes))!=-1) {
                        //byte because https is encrypted
                        Output.write(bytes, 0, length);
                        length =-1;

                        //write cache
                        WriteCache.write_cache(bytes,0,length);
                        WriteCache.write_cache("\r\n".getBytes(),0,2);
                    }
                    Output.flush();

                    try {
                        Thread.sleep(10);     //avoid this thread occupy cpu
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (SocketTimeoutException e) {
                try {
                    Input.close();
                    Output.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }catch (IOException e) {
                System.out.println(e);
            }finally {
                try {
                    Input.close();
                    Output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}
