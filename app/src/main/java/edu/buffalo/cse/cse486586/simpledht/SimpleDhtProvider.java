package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

public class SimpleDhtProvider extends ContentProvider {
    String portStr="";
    //static final String vports [] ={"11108","11112","11116","11120","11124"};
    static final String ports [] = {"5554","5556","5558","5560","5562"};
    String coordinator = "11108";
    String myKey="";
    static final int SERVER_PORT = 10000;
    String TAG = "DHT";
    String successor="";
    static int totalNodes=0;
    String predecessor="";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    LinkedList<String> chord = new LinkedList<String>();
    String col[] = {"key", "value"};
    TreeMap<String,Cursor> pendingKeys = new TreeMap();
    TreeMap<String,Integer> pendingInsert = new TreeMap();
    MergeCursor globalCursor = null;//new MatrixCursor(col);
    static int count = 0;
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        Log.d("DELETE","Request to delete file ="+selection);
        Boolean flag = false;
        flag = getContext().deleteFile(selection);
        if(!flag)
        {
            Log.d("DELETE","Key "+selection+" doesn't exist on "+ getPort(myKey) + " forwarding to successor");
           // if(!myKey.equals(successor))
             //  new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, selection+"DELETE"+String.valueOf((Integer.parseInt(getPort(myKey)) * 2)), String.valueOf((Integer.parseInt(getPort(successor)) * 2)));
        }
        else
        {
            Log.d("DELETE","Deleted file ="+selection+" at "+getPort(myKey));
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        //retrieve key from values and save value in flat file using "key" as name
        String keyHash="";
        String key,value;
        String str  = values.toString();
        String check = str.substring(0,4);
        String originatorPort[]={};
        int flag = 0;
        int roundTrip = 0;
        String selfPort = String.valueOf((Integer.parseInt(getPort(myKey)) * 2));
        Log.d("INSERT", "Uri in insert " + uri.toString());

        if(check.contains("key"))
        {
            //When key=XX value=yyvalue
            str = str.substring(4, str.length());
            int index = str.indexOf(" ");
            key = str.substring(0, index);
            value = str.substring(index + 7, str.length());
            if(value.indexOf("$$") != -1)
            {
                value= value.substring(0,value.length()-2);
                flag =1;
                Log.d("INSERT","Setting flag to 1 " + value);
            }
        }
        else {
            //When value=XX key=yy
            str = str.substring(6, str.length());
            int index = str.indexOf(" ");
            value = str.substring(0, index);
            key = str.substring(index + 5, str.length());
            if(value.indexOf("$$") != -1)
            {
                value= value.substring(0,value.length()-2);
                flag =1;
                Log.d("INSERT","Setting flag to 1 " + value);
            }
        }

        if(uri.toString().indexOf("PORT")!=-1)
        {
            originatorPort = uri.toString().split("PORT");
            Log.d("INSERT", "originatorPort for insert request of key " + key + " is "+originatorPort[1]);
            if(pendingInsert.get(key)!= null)
            {
                pendingInsert.put(key,1);
            }
            selfPort = originatorPort[1];
            if(originatorPort[1].equals(String.valueOf((Integer.parseInt(getPort(myKey)) * 2))))
            {
                pendingInsert.put(key,1);
                Log.d("INSERT","message to save key "+ key +" has come back to the originator ");
            }
        }

        Log.d("INSERT","Request to insert Key" + key + "in "+getPort(myKey));
        Log.d("INSERT", "key= "+key+" "+"value= "+value + "Uri " + uri.toString());

        try {
            keyHash = genHash(key);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        Log.d("INSERT","KeyHash ="+keyHash + " suc = "+successor+" predec = "+predecessor + " myKey " +myKey);

        Integer in = pendingInsert.get(key);

        if(("".equals(successor) && "".equals(predecessor)) || (flag == 1 && in !=null && in.intValue() == 1))
        {
            Log.d("INSERT","No Chord has been setup yet or Flag is 1");
            FileOutputStream outputStream;
            File f;
            Log.d("INSERT","Inserting Key " + key + "in "+getPort(myKey));
            try {
                outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
                outputStream.write(value.getBytes());
                outputStream.close();
                f = getContext().getFileStreamPath(key);
            } catch (Exception e) {
                Log.e("ERROR", "File write failed");
            }
            Log.d("INSERT", values.toString());
            flag = 0;
            pendingInsert.remove(key);
            return uri;
        }

        if(keyHash.compareTo(predecessor) > 0 && keyHash.compareTo(myKey) < 0) {
            FileOutputStream outputStream;
            File f;
            Log.d("INSERT","Inserting Key" + key + " in "+getPort(myKey));
            try {
                outputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
                outputStream.write(value.getBytes());
                outputStream.close();
                f = getContext().getFileStreamPath(key);
            } catch (Exception e) {
                Log.e("ERROR", "File write failed");
            }
            Log.d("INSERT", values.toString());
        }
        else //if(keyHash.compareTo(predecessor) > 0 && keyHash.compareTo(myKey) > 0)
        {
            Log.d("INSERT","Forwarding key Value to successor "+ myKey.compareTo(successor) + " suc = "+successor+" myKey " +myKey);
            Log.d("INSERT","pendingInsert size" + pendingInsert.size() + " value = "+ pendingInsert.get(key));
            Integer val = pendingInsert.get(key);
            if((myKey.compareTo(successor) > 0)){// && val !=null && val.intValue() == 1 ) {
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, key + "TURN" + value + "TURN" + "PORT" + selfPort, String.valueOf((Integer.parseInt(getPort(successor)) * 2)));
                //pendingInsert.remove(key);
                //selfPort = String.valueOf((Integer.parseInt(getPort(myKey)) * 2));
            }
            else {
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, key + "DATA" + value + "DATA" + "PORT" +selfPort, String.valueOf((Integer.parseInt(getPort(successor)) * 2)));
                pendingInsert.put(key,0);
                //selfPort = String.valueOf((Integer.parseInt(getPort(myKey)) * 2));
            }
        }
        return uri;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
         portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        try {
            myKey = genHash(portStr);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //Log.v("Hash", "Genereated hascode "+ myKey + " for avd " + portStr);

        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }

        //Send Join Request to coordinator
        if( portStr.equals("5554"))
        {

          try {
               // chord.add("11108");   //No need to send request to itself
              chord.add(genHash("5554"));
           } catch (NoSuchAlgorithmException e) {
              e.printStackTrace();
           }
        }
        else {
            String msg = "JOIN";
            msg = msg + ":" + myPort;
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
        }
        return true;
    }

    public Cursor getData(String[] list)
    {
        FileInputStream fin;
        MatrixCursor mCursor = new MatrixCursor(col);
        InputStreamReader iSR;
        BufferedReader bufferedReader;
        String value;
        for( String s: list) {
            Log.d("Request to Read", "Key=" + s);
            try {
                fin = getContext().openFileInput(s);
                iSR = new InputStreamReader(fin);
                bufferedReader = new BufferedReader(iSR);
                value = bufferedReader.readLine();
                fin.close();
                //create a new cursor
                String row[] = new String[]{s, value};
                mCursor.addRow(row);
                Log.e("Read values", "Key=" + s + "Value=" + value + "Mcursor has " + mCursor.getCount());
            } catch (Exception e) {
                Log.e("ERROR", "File read failed" + e.getMessage());
            }
        }
        return mCursor;
    }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
        Cursor m = null;
        Cursor m2 = null;
        String list[] = new String[1];
        int flag = 0;
        Log.d("query", "Uri " + uri.toString());
        if(selection.equals("\"*\""))
        {
            Log.d("STAR", "Query to look for all entries in DHT");
            if(!"".equals(successor))
            {
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, String.valueOf((Integer.parseInt(getPort(myKey)) * 2))+"FINDALL", String.valueOf((Integer.parseInt(getPort(successor)) * 2)));
                flag= flag+1;
            }
            pendingKeys.put("FINDALL",new MatrixCursor(col));

            String[] mylist = getContext().fileList();
            m = (MatrixCursor) this.getData(mylist);
            Log.d("STAR", "Query to look for all DHT entries where local machine has" + mylist.length);
            if(flag > 0) {
                while (pendingKeys.get("FINDALL").getCount() == 0) {
                    Log.e("STAR", "Waiting for response for FINDALL ");
                }
                flag =flag - 1;
            }

            m2 = pendingKeys.get("FINDALL");
            Log.d("STAR","m2.getCount "+m2.getCount());
           // MatrixCursor mtemp = (MatrixCursor) m2;
            pendingKeys.remove("FINDALL");
            if(m2.getCount() == 1 ) {
                m2.moveToFirst();
                String s = m2.getString(0);
                if (s != null && s.equals("JUNK")) {
                    Log.v("QUERY", "Found Junk entry");
                    m2 = null;
                }
            }
            Cursor[] cr = new Cursor[2];

            cr[0] = m;
            cr[1] = m2;
            MergeCursor merge = new MergeCursor(cr);
            Log.d("WAIT","Processing FINDALL with merge.count = " + merge.getCount());
            return merge;
        }
        else if(selection.equals("\"@\""))
        {

            String[] mylist = getContext().fileList();
            m = (MatrixCursor) this.getData(mylist);
            Log.d("query", "Query to look for all local entries" + mylist.length);

        }
        else
        {
             list[0] = selection;
             m=  this.getData(list);
             if(m.getCount() == 0)
             {
                 Log.d("QUERY","failed to find " + selection + "on "+getPort(myKey));
                 pendingKeys.put(selection,new MatrixCursor(col));
                 new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, selection+"GET"+String.valueOf((Integer.parseInt(getPort(myKey)) * 2)), String.valueOf((Integer.parseInt(getPort(successor)) * 2)));
                 while(pendingKeys.get(selection).getCount() == 0)
                 {
                     Log.e("WAIT","Waiting for response for key "+selection);
                 }
                 m = pendingKeys.get(selection);
                 pendingKeys.remove(selection);
             }
        }
        Log.d("mCursor",""+m.getCount());
        return m;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private String getPort(String hash)
    {
        for( String s: ports)
        {
            try {
                if(genHash(s).equals(hash))
                    return s;
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
            return null;
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String msg;
            Socket inbound = null;
            while (true) {
            try {
                inbound = serverSocket.accept();
                BufferedReader readMsg = new BufferedReader(new InputStreamReader(inbound.getInputStream()));
                msg = readMsg.readLine();
               // Log.v(TAG, "Receiving from other party");
                if(msg != null)
                    publishProgress(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
           }
        }

        protected void onProgressUpdate(String...strings) {

            String strReceived = strings[0].trim();
            String suc = "NEXT";
            String pre = "PREV";
            if(strReceived.indexOf("JOIN") != -1)
            {
                String values[] = strReceived.split(":", 2);
                Log.d("JOIN", "Receiving Join request from " + values[1]);

                //append this to LinkedList
                //chord.removeLast(); // Always remove Last and add later to make this list behave as circular list

               try {
                    //chord.add(values[1]);
                    chord.add(genHash(String.valueOf((Integer.parseInt(values[1]) / 2))));
                 } catch (NoSuchAlgorithmException e) {
                  e.printStackTrace();
                }
                Collections.sort(chord, new Comparator<String>() {
                    public int compare(String e1, String e2) {
                        return e1.compareTo(e2);
                    }
                });
                Log.d("JOIN", "Chord List has " + chord.toString());

            //forward Predecessor and Successor to each node in  chord
                int len = chord.size();
                int i=0;
                while(i<(len))
                {
                    String temp = chord.get(i);
                    ListIterator<String> itr = chord.listIterator();
                    while(itr.hasNext())
                    {
                        if(itr.next().equals(temp))
                        {
                            break;
                        }
                    }
                // Log.v("LISTE", "itr here is " +);
                    if(itr.hasNext())
                        suc = itr.next();
                    else
                        suc = chord.getFirst();

                    Iterator<String> itr1 = chord.descendingIterator();
                    while(itr1.hasNext()){
                        if(itr1.next().equals(temp))
                        {
                            break;
                        }
                    }

                    if(itr1.hasNext())
                        pre = itr1.next();
                    else
                        pre = chord.getLast();

                    i++;
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, pre+"#"+suc+"#"+len, String.valueOf((Integer.parseInt(getPort(temp)) * 2)));

                    Log.d("JOIN", "temp = " + temp  + " pre = "+pre + " suc = " + suc + "size of List is ="+len);
                    Log.d("JOIN", "temp = " + getPort(temp)  + " pre = "+getPort(pre) + " suc = " + getPort(suc) + "size of List is ="+len);
                }
            }
            else if(strReceived.indexOf("#") != -1)
            {
                String values[] = strReceived.split("#");
                successor = values[1];
                predecessor=values[0];
                totalNodes = Integer.parseInt(values[2]);
                Log.d(TAG, "Received Sucessor and Predecessor for "+getPort(myKey) + "pre =" + values[0] + " suc="+values[1]);
                Log.d(TAG, "Received Sucessor and Predecessor for "+getPort(myKey) + "pre =" + getPort(values[0]) + " suc= "+ getPort(values[1]));
            }
            else if(strReceived.indexOf("DATA") !=-1)
            {
                Log.d("TEST","Received forwarded request DATA");
                String values[] =  strReceived.split("DATA");
                ContentValues cv = new ContentValues();
                cv.put(KEY_FIELD, values[0]);
                cv.put(VALUE_FIELD, values[1]);
                Uri mUri;
                if(values.length == 3)
                    mUri = buildUri("content", values[2]);
                else
                    mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpleDht.provider");
                insert(mUri,cv);

            }
            else if(strReceived.indexOf("TURN") !=-1)
            {
                Log.d("TEST","Received forwarded request TURN");
                String values[] =  strReceived.split("TURN");
                ContentValues cv = new ContentValues();
                cv.put(KEY_FIELD, values[0]);
                cv.put(VALUE_FIELD, values[1]+"$$");
                Uri mUri;

                if(values.length == 3)
                    mUri = buildUri("content", values[2]);
                else
                    mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpleDht.provider");

                insert(mUri,cv);
            }
            else if(strReceived.indexOf("GET") !=-1)
            {
                String values[] = strReceived.split("GET",2);
                Log.d("QUERY","Received GET request for key "+values[0]+"from node "+values[1]);
                String mylist[] = new String[1];
                mylist[0] = values[0];
                MatrixCursor m = (MatrixCursor) getData(mylist);
                if(m.getCount() == 0)
                {
                    Log.d("QUERY","failed to find " + values[0] + "on "+getPort(myKey));
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, values[0]+"GET"+values[1], String.valueOf((Integer.parseInt(getPort(successor)) * 2)));
                }
                else
                {
                    Log.d("QUERY", "Found key  " + values[0] + " on " + getPort(myKey) + " " + values[1]);
                    m.moveToPosition(-1);
                    String rsp="";
                    while(m.moveToNext())
                    {
                        Log.d("QUERY","Inside cursor, key = " + m.getString(0)+ " value ="+m.getString(1));
                        rsp = m.getString(0)+"RESPONSE"+m.getString(1);
                    }
                    Log.d("QUERY", "Sending response for key  " + rsp + " to " + values[1]);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, rsp,values[1]);
                }
            }
            else if(strReceived.indexOf("RESPONSE") !=-1)
            {
                //Log.v("QUERY","Received RESPONSE for key "+strReceived);
                MatrixCursor mCursor = new MatrixCursor(col);
                String values[] = strReceived.split("RESPONSE",2);
                Log.d("QUERY","Received RESPONSE for key "+values[0]+" value = "+values[1] + "pendingKeys = "+pendingKeys.toString());
                //String row[] = new String[]{s, value};
                mCursor.addRow(values);
                pendingKeys.put(values[0],mCursor);
            }
            else if(strReceived.indexOf("FINDALL") !=-1) {
                String values[] = strReceived.split("FINDALL");
                Log.d("QUERY", "Received FINDALL Req from" + values[0]);
                try {
                    if(!successor.equals(genHash(String.valueOf((Integer.parseInt(values[0])/2)))))
                    {
                        Log.d("QUERY","Not the Last Node in the Ring for FINDALL query, forwarding to Sucessor " + values[0] + "on "+getPort(myKey));
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, values[0]+"FINDALL", String.valueOf((Integer.parseInt(getPort(successor)) * 2)));
                    }
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                String[] mylist = getContext().fileList();
                MatrixCursor m = null;
                m = (MatrixCursor) getData(mylist);
                String rsp;
                m.moveToPosition(-1);
                if (m != null) {
                    Log.d("QUERY", "Filled Matrixcursor object with localData wrt @ LocalFileCount = " + m.getCount() + " mylistCount = "+ mylist.length+ " req "+values[0]);
                    rsp = String.valueOf(m.getCount());
                    if(Integer.parseInt(rsp) == 0)
                            rsp = rsp+"RSPALL";
                    while (m.moveToNext()) {
                        Log.d("QUERY", "Inside cursor for FINDALL , key = " + m.getString(0) + " value =" + m.getString(1));
                        rsp = rsp + "RSPALL" + m.getString(0) + "RSPALL" + m.getString(1);
                    }
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, rsp, values[0]);
                }
                else{
                    Log.d("QUERY", "Matrixcursor object was null");
                }
                    //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "RSPALL", values[0]);
            }
            else if(strReceived.indexOf("RSPALL") !=-1)
            {
                Log.d("QUERY","Received RSPALL at "+getPort(myKey) +" waiting for "+ Integer.toString(totalNodes-1)+" RESPALL"  );
                count++;
                MatrixCursor mCursor = new MatrixCursor(col);
                String values[] = strReceived.split("RSPALL");
                int len = values.length;
                int i =1;
                while(i<len)
                {
                    String row [] = new String[] {values[i],values[i+1]};
                    Log.d("QUERY", "IN RSPALL key= "+ values[i] + " value = "+values[i+1]);
                    mCursor.addRow(row);
                    i=i+2;
                }
                //Log.d("QUERY","Received response for FINDALL where count = "+ values[0]);
                Cursor[] cr = new Cursor[2];
                cr[0] = mCursor;
                cr[1] = globalCursor;
                MergeCursor merge = new MergeCursor(cr);
                globalCursor = merge;
                Log.d("QUERY","RSPALL globalCursor count  = "+ merge.getCount());
                if(count == (totalNodes-1))
                {
                    Cursor cp = globalCursor;
                    if(cp.getCount() == 0)
                    {
                        String row [] = new String[] {"JUNK","JUNK"};
                        mCursor.addRow(row);
                        cp = mCursor;
                        Log.d("QUERY","RSPALL Added a Junk entry "+cp.getCount());
                    }
                    pendingKeys.put("FINDALL",cp);
                    count = 0;
                    Log.d("QUERY","RSPALL Got response from all nodes  = ");
                }
            }
            else if(strReceived.indexOf("DELETE")!=-1)
            {
                String values[] = strReceived.split("DELETE");
                Log.d("DELETE","Received delete request for key "+values[0]+" at node "+getPort(myKey));
                delete(null,values[0],null);
            }
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String remotePort = "";
            String msgToSend  ="";
            try {
                 if(msgs[0].indexOf(":") != -1) {
                     remotePort = coordinator;
                 }
                else
                 {
                    remotePort = msgs[1];
                 }
                Log.v(TAG,"In doInBackGroud with msgs[0] = " + msgs[0] + " msgs[1]="+msgs[1]);

                msgToSend = msgs[0];
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));

                OutputStream outBound = socket.getOutputStream();
                OutputStreamWriter outStream = new OutputStreamWriter(outBound);
                BufferedWriter out =  new BufferedWriter(outStream);
                out.write(msgToSend);
                out.flush();
                Log.e(TAG, "Sending msg " +msgToSend+ " to "+remotePort);
                socket.close();
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }
    }
}
