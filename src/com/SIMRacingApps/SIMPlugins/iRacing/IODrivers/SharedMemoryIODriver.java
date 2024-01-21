package com.SIMRacingApps.SIMPlugins.iRacing.IODrivers;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import com.SIMRacingApps.Server;
import com.SIMRacingApps.Windows;
import com.SIMRacingApps.SIMPlugins.iRacing.*;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SharedMemoryIODriver extends IODriver {

    

    //    private final static int    FILE_MAP_READ = 0x00000004;    //from memoryapi.h, maps to SECTION_MAP_READ in winnt.h
    private final static String IRSDK_DATAVALIDEVENTNAME = "Local\\IRSDKDataValidEvent";
    private final static String IRSDK_MEMMAPFILENAME     = "Local\\IRSDKMemMapFileName";
    private final static String IRSDK_BROADCASTMSGNAME   = "IRSDK_BROADCASTMSG";

    private Windows.MsgId m_msgId;
    private Windows.Handle m_hMemMapFile;
    private Windows.Handle m_hDataValidEvent;

    private Windows.Pointer m_pSharedMem;
//    private static final boolean m_usedirectbuffer = false;  
                                        //I don't think we want to use direct, because by the time we process the data
                                        //the shared memory could have changed. Setting this to false will use getByteArray
                                        //which I assume makes a copy of the data on then java heap. Then, the copy is what is processed.
                                        //So, I will start with false, get everything working, take benchmarks, change it and see if it matters.
                                        //if it does, come up with a test to see if the data was process before it was changed and does it matter.
                                        //TODO: benchmarks of getByteBuffer versus getByteArray

    private boolean m_initialized = false;
    private int m_prev_VarBuf = -1;
    private int m_prev_status = 0;
    private SubHeader m_subheader = new SubHeader();

    private boolean m_open() {
        if (!m_initialized) {
            m_hMemMapFile = Windows.openFileMapping(IRSDK_MEMMAPFILENAME);

            if (m_hMemMapFile != null) {
                m_pSharedMem = Windows.mapViewOfFile(m_hMemMapFile);

                if (m_pSharedMem != null) {

                    //get a handle to the event signal
                    m_hDataValidEvent = Windows.openEvent(IRSDK_DATAVALIDEVENTNAME);

                    if (m_hDataValidEvent != null) {
                        m_msgId = Windows.registerWindowMessage(IRSDK_BROADCASTMSGNAME);
                        if (m_msgId == null) {
                            Server.logger().severe(String.format(
                                    "Error(%d) registering window message %s",
                                    Windows.getLastError(),
                                    Windows.getLastErrorMessage()
                            ));
                        }
                        else {
                            m_initialized = true;
                        }
                    }
                    else {
                        if (Windows.getLastError() != Windows.ERROR_FILE_NOT_FOUND) {        //don't print file doesn't exists. iRacing may not be running
	                        Server.logger().severe(String.format(
	                                "Error(%d) opening event %s",
	                                Windows.getLastError(),
	                                Windows.getLastErrorMessage()
	                        ));
                        }
                        Windows.unmapViewOfFile(m_pSharedMem);
                        Windows.closeHandle(m_hMemMapFile);
                    }
                }
                else {
                    Server.logger().severe(String.format(
                            "Error(%d) mapping Shared Memeory %s",
                            Windows.getLastError(),
                            Windows.getLastErrorMessage()
                    ));
                    Windows.closeHandle(m_hMemMapFile);
                }
            }
            else {
                if (Windows.getLastError() != Windows.ERROR_FILE_NOT_FOUND) {        //don't print file doesn't exists. iRacing may not be running
                    Server.logger().severe(String.format(
                            "Error(%d) opening Shared Memeory %s",
                            Windows.getLastError(),
                            Windows.getLastErrorMessage()
                    ));
                }
            }
        }
        return m_initialized;
    }

    public SharedMemoryIODriver() {
        m_open();
    }

    public boolean Read(long startingtick,long endingtick) {
        if (m_open()) {
            ByteBuffer header_bytebuffer = null,sessioninfo_bytebuffer = null,varheader_bytebuffer = null,var_bytebuffer = null;

//            #####################################################################################################################
//            #This is a very time sensitive section of code.
//            #Here we read the header and the buffers that go with it
//            #Then we read the header again to see if it changed while we were reading the other data.
//            #We basically have about 32ms to read it because there are 3 buffers that are updated in a round robin.
//            #####################################################################################################################

//            if (m_usedirectbuffer)  
//                header_bytebuffer = m_pSharedMem.getByteBuffer(0, Header.SIZEOF_HEADER);
//            else
                header_bytebuffer = ByteBuffer.wrap(m_pSharedMem.getByteArray(0, Header.SIZEOF_HEADER));
            
            Header header = new Header(header_bytebuffer);

            if (header.isValid()) {
                if (header.getSessionInfoUpdate() != getHeader().getSessionInfoUpdate()
                ||  m_prev_status != header.getStatus() 
                ) {
//                    if (m_usedirectbuffer)  
//                        sessioninfo_bytebuffer = m_pSharedMem.getByteBuffer(header.getSessionInfoOffset(), header.getSessionInfoLen());
//                    else
                        sessioninfo_bytebuffer = ByteBuffer.wrap(m_pSharedMem.getByteArray(header.getSessionInfoOffset(), header.getSessionInfoLen()));

//                    if (m_usedirectbuffer)  
//                        sessioninfo_bytebuffer = m_pSharedMem.getByteBuffer(header.getSessionInfoOffset(), header.getSessionInfoLen());
//                    else
//                        sessioninfo_bytebuffer = ByteBuffer.wrap(m_pSharedMem.getByteArray(header.getSessionInfoOffset(), header.getSessionInfoLen()));

                }

                  int latest_VarBuf = header.getLatest_VarBuf();
                  if (latest_VarBuf != m_prev_VarBuf) {
//TODO: Find a better way to detect the Var Headers have changed. Can't use number of vars, because Trucks and MX5 has the same number, but different. This logic was also in the super() class. Where does it really need to be? Both?
//                      if (header.getNumVars() != getHeader().getNumVars()) {
//                          if (m_usedirectbuffer)  
//                              varheader_bytebuffer = m_pSharedMem.getByteBuffer(header.getVarHeaderOffset(), header.getNumVars() * VarHeaders.SIZEOF_VARHEADER);
//                          else
                              varheader_bytebuffer = ByteBuffer.wrap(m_pSharedMem.getByteArray(header.getVarHeaderOffset(), header.getNumVars() * VarHeaders.SIZEOF_VARHEADER));
//                      }
                        
//                      if (m_usedirectbuffer)  
//                          var_bytebuffer = m_pSharedMem.getByteBuffer(header.getVarBuf_BufOffset(latest_VarBuf), header.getBufLen());
//                      else
                          var_bytebuffer = ByteBuffer.wrap(m_pSharedMem.getByteArray(header.getVarBuf_BufOffset(latest_VarBuf), header.getBufLen()));
                  }
                  
                //#Now read the header again, so we can compare it to the previous read.
//                if (m_usedirectbuffer)  
//                    header_bytebuffer = m_pSharedMem.getByteBuffer(0, Header.SIZEOF_HEADER);
//                else
                    header_bytebuffer = ByteBuffer.wrap(m_pSharedMem.getByteArray(0, Header.SIZEOF_HEADER));

//            #####################################################################################################################
//            # Time critical end
//            #####################################################################################################################
                Header header2 = new Header(header_bytebuffer);

                if (header.isValid(header2)) {
                    m_prev_VarBuf = latest_VarBuf;
                    m_prev_status = header.getStatus();

                	//update the subheader
                	m_subheader.setSessionEndTime((double)System.currentTimeMillis()/1000.0);
                	m_subheader.setSessionRecordCount(m_subheader.getSessionRecordCount()+1);
                	//lap? not sure this belongs here. Wait until I need it. It should be in the vars, no need to duplicate here

                    return super.ProcessBuffers(
                             header_bytebuffer
                        ,    sessioninfo_bytebuffer
                        ,    varheader_bytebuffer
                        ,    var_bytebuffer
                        ,    ByteBuffer.wrap(m_subheader._getByteArray())
                    );
                }
            }
        }

        //return an empty header
        byte buffer[] = new byte[Header.SIZEOF_HEADER];
        super.ProcessBuffers(ByteBuffer.wrap(buffer), null, null, null, null);
        return false;
    }

    public void WaitForData () {
        //        WaitForSingleObject(hDataValidEvent, timeOut);
        //if iRacing is running, it will signal us every 16ms
        //Found that using a timeout of 16 was causing a delay that caused us to miss packets.
        //Changed it to 100 and no packet loss got much better. 
        //We don't need to loop very fast if iRacing isn't running.
        //NOTE: If you don't call waitForSingleObject this method, there's virtually no packet loss. 
        //      Does this mean the thread will dominate the CPU?

        if (m_open()) {
            Header header;
//            if (m_usedirectbuffer)  
//                header = new Header(m_pSharedMem.getByteBuffer(0, Header.SIZEOF_HEADER));
//            else
                header = new Header(ByteBuffer.wrap(m_pSharedMem.getByteArray(0, Header.SIZEOF_HEADER)));
            
            //Let's try only calling the Wait, if the header hasn't been updated or we're not connected.
            if ((header.getStatus() & StatusField.stConnected) == 0 || header.getLatest_VarBuf() == m_prev_VarBuf)
                Windows.waitForSingleObject(m_hDataValidEvent, 100);
        }
        else {
            //here the SIM is not publishing data, 
            //so sleep a little so we don't consume the CPU
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
            }
        }
    }

//send a remote control message to the sim
// var1, var2, and var3 are all 16 bits signed
// void irsdk_broadcastMsg(irsdk_BroadcastMsg msg, int var1, int var2, int var3);
// var2 can be a full 32 bits
// void irsdk_broadcastMsg(irsdk_BroadcastMsg msg, int var1, int var2);
    private int MAKELONG( short lowWord, short highWord) {
    	int l = (((int)highWord << 16) & 0xFFFF0000) | lowWord;
    	return l;
    }

    public void broadcastMsg(short msg, short var1, short var2, short var3)
    {
        //irsdk_broadcastMsg(msg, var1, MAKELONG(var2, var3));
        broadcastMsg(msg, var1, MAKELONG(var2,var3));
    }

    public void broadcastMsg(short msg, short var1, int var2)
    {
        if(msg >= 0 && msg < BroadcastMsg.BroadcastLast)
        {
            //SendNotifyMessage(HWND_BROADCAST, msgId, MAKELONG(msg, var1), var2);
            Server.logger().finest(String.format("SharedMemory.broadcastMsg(%d,%d,%d)",msg,var1,var2));
            Windows.sendNotifyMessage(m_msgId,MAKELONG(msg,var1),var2);
        }
    }

    public void close() {
        if (!m_initialized) {
            Windows.closeHandle(m_hDataValidEvent);
            Windows.unmapViewOfFile(m_pSharedMem);
            Windows.closeHandle(m_hMemMapFile);
            m_initialized = false;
        }
    	super.close();
    }

    public void finalize() throws Throwable {
    	close();
        super.finalize();
    }
}
