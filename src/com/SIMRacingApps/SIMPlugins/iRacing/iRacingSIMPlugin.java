package com.SIMRacingApps.SIMPlugins.iRacing;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Level;

import com.SIMRacingApps.SIMPlugin;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Session;
import com.SIMRacingApps.SIMPlugins.iRacing.VarHeaders.VarHeader;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.*;
import com.SIMRacingApps.Util.FindFile;
import com.owlike.genson.Genson;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2022 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class iRacingSIMPlugin extends SIMPlugin {

    
    
    private iRacingSession m_session;
    private double m_sessionTime = 999999999.0;
    
    private boolean m_initialized = false;
    private IODriver m_IODriver;
    private Genson genson = new Genson();

    public IODriver getIODriver() { _init(); return m_IODriver; }
    
    public iRacingSIMPlugin() throws SIMPluginException { 
        super();
        
        m_session = new iRacingSession(this);
    }
    
    @Override
    public Session getSession() {
        return m_session;
    }
    
//    @Override
//    public Data getSIMName() {
//        return super.getSIMName().setValue("iRacing").setState(Data.State.NORMAL);
//    }

    @Override
    public Data setPlay(String file) {
        if (file == null)
            return getPlay();

        if (super.getPlay() != null && !super.getPlay().isEmpty() && super.getPlay().equals(file))
            return getPlay();

        if (m_IODriver != null)
            m_IODriver.close();
        super.setPlay(file);
        m_initialized = false;
        _init();
        return getPlay();
    }

    @Override
    public Data setRecord(String file) {
        if (file == null)
            return getRecord();

        if (super.getRecord() != null && !super.getRecord().isEmpty() && super.getRecord().equals(file))
            return getRecord();

        super.setRecord(file);
        _init();
        
        if (m_IODriver != null)
            m_IODriver.Record(this.getRecord().getString());    //The base class does all the recording and sets the path

        return getRecord();
    }


    private boolean _init() {
        if (!m_initialized) {

            if (getPlay() != null && !getPlay().isEmpty()) {
                File playfile = new File(getPlay().getString());
                
                //TODO: here's where I will put a RemoteConnector if I can detect Play() points to a url, starts with http?
                if (getPlay().getString().toUpperCase().endsWith(".IBT")) {
                    try {
                        if (!playfile.isFile()) {
                            String s = FindFile.getUserDocumentsPath();
                            if (s != null && !s.isEmpty()) {
                                String datadir = Server.getArg("iracing-datadir","iRacing");
                            
                                playfile = new File(s + "\\"+datadir+"\\Telemetry\\" + playfile.getName());
                            }
                        }
                        
                        if (!playfile.isFile())
                            throw new FileNotFoundException(playfile.toString());
                            
                        Server.logger().info(String.format("loading IBTFileIODriver(%s)", playfile.toString()));
                        m_IODriver = new IBTFileIODriver( playfile.toString() );
                    }
                    catch (FileNotFoundException e) {
                        Server.logStackTrace(Level.WARNING,"File not found, loading SharedMemoryIODriver",e);
                        m_IODriver = new SharedMemoryIODriver();
                    }
                }
                else {
                    if (!playfile.isFile())
                        playfile = new File(FindFile.getUserPath()[0] + "\\" + getPlay().getString());
                    
                    if (!playfile.isFile())
                        playfile = new File(FindFile.getUserPath()[0] + "\\recordings\\" + playfile.getName());
                    
                    Server.logger().info(String.format("loading FileIODriver(%s)", playfile.toString()));
                    try {
                        m_IODriver = new FileIODriver( playfile.toString() );
                    } catch (FileNotFoundException e) {
                        Server.logStackTrace(Level.WARNING,"File not found, loading SharedMemoryIODriver",e);
                        m_IODriver = new SharedMemoryIODriver();
                    }
                }
            }
            else {
                Server.logger().info("loading SharedMemoryIODriver()");
                m_IODriver = new SharedMemoryIODriver();
            }

            m_initialized = true;
        }
        return m_initialized;
    }

    @Override
    public boolean isActive() {
        if (!_init()) return false;
        if (getPlay() != null && !getPlay().isEmpty() && m_IODriver.isEOF()) {
            m_initialized = false;
            return true;
        }
        return !m_IODriver.isEOF();
    }

//    @Override
//    public boolean isInitialized() {
//        return m_initialized;
//    }

//    private boolean m_isdataready = false;

//    @Override
//    public boolean isDataReady() { return m_isdataready; }

    @Override
    public boolean isConnected() {
        if (!_init()) return false;
        int status = m_IODriver.getHeader().getStatus();
        return (status & StatusField.stConnected) != 0;
    }

    private Long m_startingVersion = null;
    private Long m_endingVersion   = null;
    
    @Override
    public boolean waitForDataReady() {
        if (!_init()) return false;

        m_IODriver.WaitForData();    //let's each connector implement a throttle for the data before we try to read it.

        if (m_startingVersion == null) {
            String s[] = getStartingVersion().getString().split("[-]");
            if (s.length == 1 && !s[0].isEmpty())
                m_startingVersion = Long.parseLong(s[0]);
            else
            if (s.length == 2 && !s[1].isEmpty())
                m_startingVersion = Long.parseLong(s[1]);
            else
                m_startingVersion = 0L;
            
    
            s = getEndingVersion().getString().split("[-]");
            if (s.length == 1 && !s[0].isEmpty())
                m_endingVersion = Long.parseLong(s[0]);
            else
            if (s.length == 2 && !s[1].isEmpty())
                m_endingVersion = Long.parseLong(s[1]);
            else
                m_endingVersion = Long.MAX_VALUE;
            
            Server.logger().info(String.format("iRacing: Starting Version(%d), Ending Version(%d)",m_startingVersion,m_endingVersion));
        }
        
        //don't read new data without locking first
        //otherwise, other threads could be reading
        //TODO: change this to concurrent read write lock to allow multiple clients to read at the same time.
        synchronized (this) {
            if (m_IODriver.Read(m_startingVersion,m_endingVersion)) {
//                return m_isdataready = m_session.processData();
                
                if (isConnected()) {
                    //test if iRacing has started a new session and create a new one here as well
                    if (m_session.getTimeElapsed().getDouble() < m_sessionTime && !m_session.getIsReplay().getBoolean()) {
//found that this condition can be caused when spectating a race and you rewind and look at the replay.
//So, for now just log it as I don't think I really need this to create a new session.                        
//                        m_session = new iRacingSession(this);
                        Server.logger().warning(String.format("iRacingSIMPlugin: SessionTime(%f) less than last(%f)", m_session.getTimeElapsed().getDouble(), m_sessionTime)); 
                    }

                    m_sessionTime = m_session.getTimeElapsed().getDouble();
                    return m_session.processData();
                }
            }
        }
        
//        return m_isdataready = false;
        return false;
    }

    @Override
    public void close() {
        m_IODriver.close();
        super.close();
    }

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////      OVERRIDES   ///////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//    @Override
//    public void recordString(String cmd) {
//        super.recordString(cmd);
//        m_IODriver.recordCommand(cmd);
//    }

    //This will give you direct access to the data without interpretation.
    //please only use for debugging and design. Use wrappers for production code.
    //I suppose if a new field comes out, use this until a wrapper is written.
    @Override
    public Data getSIMData(String ... args) {
        StringBuffer name = new StringBuffer("");
        for (int i=0; i < args.length; i++) {
            if (!args[i].equals("")) {
                if (i > 0) {
                    name.append("/"+args[i]);
                }
                else {
                    name.append(args[i]);
                }
            }
        }

        Data d = new Data(name.toString(),"iRacing ERROR: ("+name+") INVALID");
        d.setState(Data.State.ERROR);

//        if (args.length == 0)
//            return d;
//
        int argsindex = 0;

        if (args.length - argsindex > 0) {
            if (args[argsindex].equalsIgnoreCase("Vars")) {
                if (args.length - (argsindex+1) <= 0) {
                    d = new Data(args[argsindex],genson.serialize(m_IODriver.getVars().getVars()),"JSON",Data.State.NORMAL);
                }
                else {
                    VarHeader varHeader = m_IODriver.getVarHeaders().getVarHeader(args[argsindex+1]);
                    if (args.length > argsindex+1 && varHeader != null) {
                        Object var = m_IODriver.getVars().getVars(args[argsindex+1]).get(args[argsindex+1]);
                        String uom = varHeader.Unit;
                        d = new Data(args[argsindex],var,uom,Data.State.NORMAL);
                        if (args.length > argsindex + 2)
                            d = d.convertUOM(args[argsindex+2]);
                    }
                }
            }
            else
            if (args[argsindex].equalsIgnoreCase("VarHeaders")) {
                if (args.length - (argsindex+1) <= 0) {
                    Map<String,VarHeader> headers = m_IODriver.getVarHeaders().getVarHeaderValues(m_IODriver.getVars());
                    d = new Data(args[argsindex],genson.serialize(headers),"JSON",Data.State.NORMAL);
                }
                else
                if (args.length > argsindex+1 && m_IODriver.getVarHeaders().getVarHeader(args[argsindex+1]) != null) {
                    String field = args[argsindex+1];
                    VarHeader header = m_IODriver.getVarHeaders().getVarHeaderValue(args[argsindex+1],m_IODriver.getVars());
                    d = new Data(field,genson.serialize(header),"JSON",Data.State.NORMAL);
                }
            }
            else
            if (args[argsindex].equalsIgnoreCase("Header")) {
                String header = m_IODriver.getHeader().toJSON();
                d = new Data(args[argsindex],header,"JSON",Data.State.NORMAL);
            }
            else
            if (args[argsindex].equalsIgnoreCase("BroadcastMessage") || args[argsindex].equalsIgnoreCase("Broadcast") || args[argsindex].equalsIgnoreCase("BroadcastMsg")) {
                String s = BroadcastMsg.send(
                    m_IODriver,
                    args.length - argsindex > 1 ? args[argsindex+1] : "",
                    args.length - argsindex > 2 ? args[argsindex+2] : "",
                    args.length - argsindex > 3 ? args[argsindex+3] : "",
                    args.length - argsindex > 4 ? args[argsindex+4] : ""
                );
                d = new Data(args[argsindex],s,"String",Data.State.NORMAL);
                d.add("SET",true,"boolean"); //tells the web server not to cache this.
            }
            else {
                Data d2 = new Data(name.toString(),m_IODriver.getSessionInfo().getString(args),"JSON");
                if (d2 != null && !d2.getString().equals("")) {
                    d = d2;
                    d.setState(Data.State.NORMAL);
                }
            }
        }
        else {
            @SuppressWarnings("unchecked")
            Map<String,Object> o = new TreeMap<String,Object>((Map<String, Object>) m_IODriver.getSessionInfo().getObject(args));
            o.put("VarHeaders",m_IODriver.getVarHeaders().getVarHeaderValues(m_IODriver.getVars()));
            o.put("Vars",m_IODriver.getVars().getVars());

            Data d2 = new Data(name.toString(),m_IODriver.getSessionInfo().getString(o),"JSON");
            if (d2 != null && !d2.getString().equals("")) {
                d = d2;
                d.setState(Data.State.NORMAL);
            }
        }

        return d;
    }

}
