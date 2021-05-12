package com.SIMRacingApps.SIMPlugins.iRacing;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Car.LapType;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Session;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugin.SIMPluginException;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Windows;
import com.SIMRacingApps.SIMPlugins.iRacing.BroadcastMsg.ReloadTexturesMode;
import com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache.*;
import com.SIMRacingApps.Util.FindFile;
import com.SIMRacingApps.Util.SendKeys;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2021 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class iRacingSession extends com.SIMRacingApps.Session {

    public static final String IRACING_TITLE = "iRacing.com Simulator";
    
    private iRacingSIMPlugin m_SIMPlugin;
    
    public iRacingSession(iRacingSIMPlugin SIMPlugin) {
        super(SIMPlugin);
        m_SIMPlugin = SIMPlugin;
        Server.logger().info("iRacing Session Created");
        _init();
    }

    //These values will be cached because the session data doesn't change very much and it's expensive to parse every time.
    private int                                      m_sessionUniqueID;
    private int                                      m_dataVersion;
    private int                                      m_sessionVersion;
    private int                                      m_previousSessionNumber = -1;
    private boolean                                  m_connected = false;
    private boolean                                  m_seenGreen = false;
    private String                                   m_status = Status.UNKNOWN;
    private boolean                                  m_hasIncidents;
    private boolean                                  m_isOfficial;
    private iRacingTrack                             m_track;
    private SessionDataCars                          m_cars;
    private SessionDataCarsByCarNumber               m_carsByCarNumber;
    private SessionDataCarsSortedByCarNumber         m_carsSortedByCarNumber;
    private SessionDataCarsByPosition                m_carsByPosition;
    private SessionDataCarsByPositionClass           m_carsByPositionClass;
    private SessionDataCarsByRelativeClass           m_carsByRelativeClass;
    private SessionDataCarsByRelativeLocationClass   m_carsByRelativeLocationClass;
    private SessionDataCarsByRelativePosition        m_carsByRelativePosition;
    private SessionDataCarsByRelativePositionClass   m_carsByRelativePositionClass;
    private SessionDataCarsByRelative                m_carsByRelative;
    private SessionDataCarsByRelativeLocation        m_carsByRelativeLocation;
    private Map<String,Object>                       m_sendKeys;
    private String                                   m_cameraFocusOn = "DRIVER"; //TODO: report to iRacing need to know what current focus is
    
    private void _init() {
        m_sessionUniqueID                = -1;
        m_dataVersion                    = -1;
        m_sessionVersion                 = -1;
        m_connected                      = false;
        m_hasIncidents                   = false;
        m_isOfficial                     = m_SIMPlugin.getIODriver().getSessionInfo().getBoolean("WeekendInfo","Official");
        m_track                          = new iRacingTrack(m_SIMPlugin);
        m_cars                           = new SessionDataCars(m_SIMPlugin);
        m_carsByCarNumber                = new SessionDataCarsByCarNumber(m_SIMPlugin,m_cars);
        m_carsSortedByCarNumber          = new SessionDataCarsSortedByCarNumber(m_SIMPlugin,m_cars);
        m_carsByPosition                 = new SessionDataCarsByPosition(m_SIMPlugin,m_cars);
        m_carsByPositionClass            = new SessionDataCarsByPositionClass(m_SIMPlugin,m_cars);
        m_carsByRelativeClass            = new SessionDataCarsByRelativeClass(m_SIMPlugin,m_cars);
        m_carsByRelativeLocationClass    = new SessionDataCarsByRelativeLocationClass(m_SIMPlugin,m_cars);
        m_carsByRelativePosition         = new SessionDataCarsByRelativePosition(m_SIMPlugin,m_cars);
        m_carsByRelativePositionClass    = new SessionDataCarsByRelativePositionClass(m_SIMPlugin,m_cars);
        m_carsByRelative                 = new SessionDataCarsByRelative(m_SIMPlugin,m_cars);
        m_carsByRelativeLocation         = new SessionDataCarsByRelativeLocation(m_SIMPlugin,m_cars);
        try {
            FindFile file                = new FindFile("com/SIMRacingApps/SIMPlugins/iRacing/SendKeys.json");
            m_sendKeys                   = file.getJSON();
        } catch (FileNotFoundException e) {
            Server.logStackTrace(Level.WARNING,"SendKeys.json not found",e);
        }
        SendKeys.setDriver((SendKeys.Driver.valueOf(SendKeys.Driver.class, Server.getArg("iracing-sendkeys-driver","WINDOWS"))));
        SendKeys.setWindowName(Server.getArg("iracing-title",IRACING_TITLE));
        SendKeys.setDelay(0);
    }

    public boolean _isOfficial() { return m_isOfficial; }
    
    @Override
    public Track getTrack() {
        return m_track;
    }
    
    public void _setHasIncidents(int incidents) {
        if (incidents > 0)
            m_hasIncidents = true;
    }
    
    public boolean _getHasIncidents() {
        return m_hasIncidents;
    }
    
    private int _CarIdx(String car) {
        int idx = -1;
        try {
            if (car == null
            ||  car.isEmpty()
            ||  car.equalsIgnoreCase("REFERENCE")
            ||  car.equalsIgnoreCase("R0")
            ||  car.equalsIgnoreCase("RL0")
            ||  car.equalsIgnoreCase("RP0")
            ||  car.equalsIgnoreCase("RPC0")
            ) {
                if (!car.equalsIgnoreCase(getReferenceCar().getString()))
                    idx = _CarIdx(getReferenceCar().getString());
            }
            else
            if (car.equalsIgnoreCase("ME")) {
                if (m_SIMPlugin.isConnected()) {
                    idx = m_SIMPlugin.getIODriver().getVars().getInteger("PlayerCarIdx");
                    if (idx < 0)
                        idx = Integer.parseInt(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarIdx"));
                }
            }
            else
            if (car.equalsIgnoreCase("PACE")
            ||  car.equalsIgnoreCase("SAFETY")
            ||  car.equalsIgnoreCase("PACECAR")
            ||  car.equalsIgnoreCase("SAFETYCAR")
            ||  car.equalsIgnoreCase("P0")
            ||  car.equalsIgnoreCase("PC0")
            ) {
                if (m_SIMPlugin.isConnected()) {
                    iRacingCar c = m_cars.getPaceCar();
                    idx = (c == null ? -1 : c.getId().getInteger());
                }
            }
            else
            if (car.equalsIgnoreCase("TRANSMITTING")) {
                if (m_SIMPlugin.isConnected()) {
                    idx = m_SIMPlugin.getIODriver().getVars().getInteger("RadioTransmitCarIdx");
                    idx = Server.getArg("idx-transmitting", idx);   //for debugging
                }
                
                if (idx == -1 && Server.getArg("teamspeak-transmitting", true)) {
                    //see if we can detect if anyone is talking on TeamSpeak
                    try {
                        String teamspeakName = m_SIMPlugin.getData("TeamSpeak/Talker").getString();
                        iRacingCar c = m_cars.getByName(teamspeakName);
                        idx = (c == null ? -1 : c.getId().getInteger());
                    } catch (SIMPluginException e) {
                    }
                }
            }
            else
            if (car.equalsIgnoreCase("BEST")) {
                if (m_SIMPlugin.isConnected()) {
                    iRacingCar c = m_cars.getBest();
                    idx = (c == null ? -1 : c.getId().getInteger());
                }
            }
            else
            if (car.equalsIgnoreCase("FASTEST")) {
                if (m_SIMPlugin.isConnected()) {
                    iRacingCar c = m_cars.getFastest();
                    idx = (c == null ? -1 : c.getId().getInteger());
                }
            }
            else
            if (car.equalsIgnoreCase("CRASHES") || car.equalsIgnoreCase("EXCITING")) {
                if (m_SIMPlugin.isConnected() && m_SIMPlugin.getIODriver().getVars().getBoolean("IsReplayPlaying")) {
                    iRacingCar c = m_cars.getCar(m_SIMPlugin.getIODriver().getVars().getInteger("CamCarIdx"));
                    idx = (c == null ? -1 : c.getId().getInteger());
                }
            }
            else
            if (car.equalsIgnoreCase("PITSTALL") || car.equalsIgnoreCase("I-2")) {
                return -2;
            }
            else
            if (car.equalsIgnoreCase("LEADERCLASS")) {
                if (getReferenceCar().getString().equals("LEADERCLASS"))
                    idx = m_carsByPositionClass.getCarIdx(getCar("ME").getClassName().getString(),1);
                else
                    idx = m_carsByPositionClass.getCarIdx(getCar("REFERENCE").getClassName().getString(),1);
            }
            else
            if (car.toUpperCase().startsWith("LEADER")) {
                if (car.equalsIgnoreCase("LEADER"))
                    idx = m_carsByPosition.getCarIdx(1);
                else {
                    String c = car.substring(6);
                    idx = m_carsByPositionClass.getCarIdx(getCar(c).getClassName().getString(),1);
                }
            }
            else
            if (car.matches("[Rr][Pp][Cc].*")) {
                int position = Integer.parseInt(car.substring(3));
                idx = m_carsByRelativePositionClass.getCarIdx(position);
            }
            else
            if (car.matches("[Rr][Ll][Cc].*")) {
                int position = Integer.parseInt(car.substring(3));
                idx = m_carsByRelativeLocationClass.getCarIdx(position);
            }
//TODO: To support Position by class, what class to get the position of?            
//            else
//            if (car.matches("[Pp][Cc].*")) {
//                int position = Integer.parseInt(car.substring(2));
//                if (position > 0) {   //note: positions start at 1 for first place, but array is zero based.
//                    String className = ????;
//                    idx = m_carsByPositionClass.getCarIdx(className,position);
//                }
//            }
            else
            if (car.matches("[Rr][Cc].*")) {
                int position = Integer.parseInt(car.substring(2));
                idx = m_carsByRelativeClass.getCarIdx(position);
            }
            else
            if (car.matches("[Rr][Ll].*")) {
                int position = Integer.parseInt(car.substring(2));
                idx = m_carsByRelativeLocation.getCarIdx(position);
            }
            else
            if (car.matches("[Rr][Pp].*")) {
                int position = Integer.parseInt(car.substring(2));
                idx = m_carsByRelativePosition.getCarIdx(position);
            }
            else
            if (car.matches("[iI][0-9]") || car.matches("[iI][1-9][0-9]")) {
                idx = Integer.parseInt(car.substring(1));
            }
            else
            if (car.matches("[Nn][Ss].*")) {
                int position = Integer.parseInt(car.substring(2));
                idx = m_carsSortedByCarNumber.getCarIdx(position);
            }
            else
            if (car.matches("[Pp][Cc].*")) {
                int position = Integer.parseInt(car.substring(2));
                idx = m_carsByPositionClass.getCarIdx(getCar("REFERENCE").getClassName().getString(),position);
            }
            else
            if (car.matches("[Pp].*")) {
            //if (car.matches("[Pp][0-9]") || car.matches("[Pp][1-9][0-9]")) {
                int position = Integer.parseInt(car.substring(1));
                if (position > 0) {   //note: positions start at 1 for first place, but array is zero based.

                    idx = m_carsByPosition.getCarIdx(position);
                }
            }
            else
            if (car.matches("[Rr].*")) {
                int position = Integer.parseInt(car.substring(1));
                idx = m_carsByRelative.getCarIdx(position);
            }
            else
            if (car.matches("[Nn].*")) {
                idx = m_carsByCarNumber.getCarIdx(car.substring(1));
            }
            else {
                idx = m_carsByCarNumber.getCarIdx(car);
            }
        }
        catch (NumberFormatException e) {}

        return idx < 0 || idx >= m_cars.getInteger() ? -1 : idx;
    }

//    private int _MaxCars() {
//        int max = 64;
//
//        if (m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLap") != null)
//            max = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLap").Count;
//
//        return max;
//    }

    @Override
    public String getSendKeys(String group, String command) {
        String keys = "";
        
        if (m_sendKeys != null) {
            @SuppressWarnings("unchecked")
            Map<String,String> groupMap = (Map<String,String>)m_sendKeys.get(group);
            if (group != null && groupMap.containsKey(command)) {
                return groupMap.get(command);
            }
        }
        return keys;
    }

    private class _camera {
        String name;
        String group;
        int groupNumber;
        
        _camera(String group, int groupNumber) {
            this.name = group;
            this.group = group.toLowerCase();
            this.groupNumber = groupNumber;
        }
    };
    
    private Map<String,_camera> m_camera_groups = null;
    private Map<String,_camera> m_camera_groups_by_number = null;
    private ArrayList<String> m_camera_groups_array = null;
    
    private boolean _loadCameras() {
        if (m_SIMPlugin.isConnected()) {
            Map<String,_camera> camera_groups = new TreeMap<String,_camera>();
            
            @SuppressWarnings("unchecked")
            ArrayList<Map<String,Object>> groups = (ArrayList<Map<String,Object>>)m_SIMPlugin.getIODriver().getSessionInfo().getObject("CameraInfo","Groups");

            for (int groupIdx = 0; groupIdx < groups.size(); groupIdx++) {
                String groupName = (String) groups.get(groupIdx).get("GroupName");
                int groupNumber  = (int) groups.get(groupIdx).get("GroupNum");
                
                _camera camera = new _camera(groupName,groupNumber);
                camera_groups.putIfAbsent(camera.group,camera);
                //Server.logger().info(String.format("Camera #%d = %s", groupNumber, groupName));
            }
            
            //if (m_camera_groups == null || camera_groups.size() != m_camera_groups.size()) {
                //now create the sorted arrays
                Iterator<Entry<String, _camera>> itr = camera_groups.entrySet().iterator();
                m_camera_groups_array = new ArrayList<String>();
                m_camera_groups_by_number = new HashMap<String,_camera>();
                while (itr.hasNext()) {
                    _camera camera = itr.next().getValue();
                    m_camera_groups_array.add(camera.name);
                    m_camera_groups_by_number.put(String.format("%d",camera.groupNumber), camera);
                    if (m_camera_groups == null || camera_groups.size() != m_camera_groups.size())
                        Server.logger().info(String.format("Camera #%d = %s", camera.groupNumber, camera.name));
                }
                m_camera_groups = camera_groups;
            //}
        }
        else {
            m_camera_groups = null;
        }
        
        return m_camera_groups != null;
    }
    
    @Override
    public    Data getCamera() {
        Data d = super.getCamera();
        d.setState(Data.State.OFF);
        
        if (_loadCameras()) {
            int groupNumber   = m_SIMPlugin.getIODriver().getVars().getInteger("CamGroupNumber");
            _camera camera = m_camera_groups_by_number.get(String.format("%d",groupNumber));
            
            if (camera != null)
                d.setValue(camera.name,"",Data.State.NORMAL);
        }
        
        return d;
    }
    
    @Override
    public    Data getCameraFocus() {
        Data d = super.getCamera();
        d.setState(Data.State.OFF);
        
        if (_loadCameras()) {
            d.setValue(m_cameraFocusOn,"",Data.State.NORMAL);
        }
        
        return d;
    }
    
    @Override
    public    Data getCameras() {
        Data d = super.getCameras();
        d.setState(Data.State.OFF);
        
        if (_loadCameras()) {
            d.setValue(m_camera_groups_array,"",Data.State.NORMAL);
        }
        
        return d;
    }

    iRacingCar m_defaultCar = null;
    
    @Override
    public Car getCar(String car) {
//        Car s = super.getCar(car);
        Car c =  m_cars.getCar(_CarIdx(car));
        
        if (c != null)
            return c;
        
        if (m_defaultCar == null)
            m_defaultCar = new iRacingCar(m_SIMPlugin);
        
        return m_defaultCar;
    }
    
    @Override
    public Data getCars() {
        Data d = super.getCars();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int cars = m_cars.getNumberOfCars();
            d.setValue(cars);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getCautionLaps() {
        Data d = super.getCautionLaps();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected() && _CarIdx("LEADER") > -1) {
            d.setValue(m_cars.getCar(_CarIdx("LEADER")).getLap(Car.LapType.CAUTION).getInteger());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getCautions() {
        Data d = super.getCautions();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected() && _CarIdx("LEADER") > -1) {
            d.setValue(m_cars.getCar(_CarIdx("LEADER")).getCautions().getInteger());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getClassNames() {
        Data d = super.getClassNames();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected() && this.getNumberOfCarClasses().getInteger() > 1) {
            Map<Integer,String> classes = new TreeMap<Integer,String>(Collections.reverseOrder());
            
            for (int driversIdx=0; driversIdx < 64; driversIdx++) {
                String sDriversIdx = Integer.toString(driversIdx);
                String sRelSpeed   = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"CarClassRelSpeed");
                String className   = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"CarClassShortName");

                if (!sRelSpeed.isEmpty() && !className.isEmpty()) {
                    classes.put(Integer.parseInt(sRelSpeed), className);
                }
            }
            
            ArrayList<String> a = new ArrayList<String>();
            
            Iterator<Entry<Integer, String>> itr = classes.entrySet().iterator();
            while (itr.hasNext()) {
                a.add(itr.next().getValue());
            }
            d.setValue(a);
        }
        return d;
    }
    
    @Override
    public Data getDataVersion() {
        Data d = super.getDataVersion();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            d.setValue(
                Integer.toString(m_sessionVersion)
              + "-" 
              + Integer.toString(m_dataVersion)
            );
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getId() {
        Data d = super.getId();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String seasonID = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","SeasonID");
            String seriesID = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","SeriesID");
            String sessionID = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","SessionID");
            String subSessionID = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","SubSessionID");
            d.setValue(String.format("%s/%s/%s/%s",seasonID,seriesID,sessionID,subSessionID ),"",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getIncidentLimit() {
        Data d = super.getIncidentLimit();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","IncidentLimit");
            if (!s.isEmpty()) {
                if (s.equals("unlimited"))
                    d.setValue(9999,d.getUOM(),Data.State.NORMAL);
                else
                    d.setValue(Integer.parseInt(s),d.getUOM(),Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getIsGreenFlag() {
        Data d = super.getIsGreenFlag();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.green) > 0)
                d.setValue(true);
            
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getIsCautionFlag() {
        Data d = super.getIsCautionFlag();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;

            flags = (flags == 0
                    &&
                    this.getIsReplay().getBoolean() 
                    &&
                    getType().equals(Session.Type.RACE) 
                    &&
                    this.m_SIMPlugin.getSession().getCar("PACECAR").getStatus().equals(Car.Status.ONTRACK)
                    &&
                    this.m_SIMPlugin.getSession().getLaps().getInteger() > 1
                   ) 
                 ? SessionFlags.caution 
                 : flags;
            
            if ((flags & SessionFlags.caution) > 0 || (flags & SessionFlags.cautionWaving) > 0)
                d.setValue(true);
            d.setState(Data.State.NORMAL);
            
        }
        return d;
    }

    @Override
    public Data getIsCheckeredFlag() {
        Data d = super.getIsCheckeredFlag();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            flags = (flags == 0
                    &&
                    this.getIsReplay().getBoolean() 
                    &&
                    getType().equals(Session.Type.RACE) 
                    &&
                    this.m_SIMPlugin.getSession().getLaps().getInteger() > 1
                    && 
                    this.m_SIMPlugin.getSession().getLapsToGo().getInteger() == 0
                    ) 
                  ? SessionFlags.checkered 
                  : flags;
            
            if ((flags & SessionFlags.checkered) > 0) {
                d.setValue(true);
            }
            d.setState(Data.State.NORMAL);
            
        }
        return d;
    }
    
    @Override
    public Data getIsCrossedFlag() {
        Data d = super.getIsCrossedFlag();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            //even though iRacing has a crossed flag bit in the SessionFlags, I've never seen it set
            //so, I will calculate it myself as the halfway point based on the laps remaining in the session.
            if (getType().equals(Session.Type.RACE)) {
                long togo = getLapsToGo().getInteger();
                double laps = getLaps().getDouble();
                long halfway = Math.round((laps/2.0)-0.1);
                if (togo == halfway) {
                    d.setValue(true);
                }
            }
            d.setState(Data.State.NORMAL);
            
        }
        return d;
    }
    
    @Override
    public Data getIsWhiteFlag() {
        Data d = super.getIsWhiteFlag();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            flags = (flags == 0
                    &&
                    this.getIsReplay().getBoolean() 
                    &&
                    getType().equals(Session.Type.RACE) 
                    &&
                    this.m_SIMPlugin.getSession().getLaps().getInteger() > 1
                    && 
                    this.m_SIMPlugin.getSession().getLapsToGo().getInteger() == 1
                   ) 
                 ? SessionFlags.white 
                 : flags;
            
            if ((flags & SessionFlags.white) > 0)
                d.setValue(true);
            d.setState(Data.State.NORMAL);
            
        }
        return d;
    }
    
    @Override
    public Data getIsRedFlag() {
        Data d = super.getIsRedFlag();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.red) > 0
            ||  (!(getType().equals(Session.Type.RACE) || getType().equals(Session.Type.LONE_QUALIFY))
                   && (flags & SessionFlags.checkered) > 0
                )
            )
                d.setValue(true);
            d.setState(Data.State.NORMAL);
            
        }
        return d;
    }
    
    @Override
    public Data getIsReplay() {
        Data d = super.getIsReplay();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            boolean flag = m_SIMPlugin.getIODriver().getVars().getBoolean("IsReplayPlaying");
            d.setValue(flag);
            d.setState(Data.State.NORMAL);
            
        }
        return d;
    }
    
    @Override
    public Data getLap() {
        Data d = super.getLap();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String sessiontype = getType().getString();
            int lap  = sessiontype.equals(Session.Type.RACE)
                     ? getCar("LEADER").getLap(iRacingCar.LapType.COMPLETED).getInteger() + 1
                     : getCar(getReferenceCar().getString()).getLap(iRacingCar.LapType.COMPLETED).getInteger() + 1;
            d.setValue(lap);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getLaps(String sessionType) {
        Data d = super.getLaps(sessionType);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int session    = -1;
            
            if (!sessionType.isEmpty()) {
                String type;
                for (int i=0; !(type = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(i),"SessionType")) .isEmpty();i++) {
                    if (type.equalsIgnoreCase(sessionType))
                        session = i;
                }
            }
            else {
                session = m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum");
            }
            
            if (session > -1) {
                String laps    = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(session),"SessionLaps");
    
                if (laps.isEmpty() || laps.equals("unlimited"))
                    d.setValue(UNLIMITEDLAPS);
                else
                    d.setValue(Integer.parseInt(laps));
    
                d.setState(Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getLapsToGo() {
        Data d = super.getLapsToGo();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            boolean isRace = getType().getString().equalsIgnoreCase(Type.RACE);
            Data togo      = isRace 
                           ? getCar("LEADER").getLapsToGo()
                           : getCar("REFERENCE").getLapsToGo();
            
            d.setValue(togo.getInteger(),togo.getUOM());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getLeagueId() {
        Data d = super.getId();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String ID = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","LeagueID");
            d.setValue(ID,"",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getMessages() {
        Data d = super.getMessages();
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            StringBuffer flagnames = new StringBuffer("");

            if ((flags & SessionFlags.debris) > 0)        { flagnames.append(";"); flagnames.append(Session.Message.DEBRIS); }
            if ((flags & SessionFlags.oneLapToGreen) > 0
//            &&  getCarLap("P1").getInteger() <= getLaps().getInteger()
            &&  !getType().equals(Session.Type.OFFLINE_TESTING)
            &&  (flags & SessionFlags.checkered) == 0     //iRacing is setting the one to go when the checkered is out
            )                                             { flagnames.append(";"); flagnames.append(Session.Message.ONELAPTOGREEN); }
//In the May 2020 build, this flag was changed to indicate the restart is pending
//            if ((flags & SessionFlags.greenHeld) > 0)     { flagnames.append(";"); flagnames.append(Session.Message.GREENHELD); }
            if ((flags & SessionFlags.startReady) > 0)    { flagnames.append(";"); flagnames.append(Session.Message.STARTREADY); }
            if ((flags & SessionFlags.startSet) > 0)      { flagnames.append(";"); flagnames.append(Session.Message.STARTSET); }
            if ((flags & SessionFlags.startGo) > 0)       { flagnames.append(";"); flagnames.append(Session.Message.STARTGO); }

            // I haven't seen iRacing set these yet, even so, the spotter normally announces it for me and not the leader.
            //check the leader if there is one to get the remaining laps.
//            int completedLeader    = getCar("LEADER").getLap(iRacingCar.LapType.COMPLETED).getInteger();
//            int completedReference = getCar("REFERENCE").getLap(iRacingCar.LapType.COMPLETED).getInteger();
//            int togo = getLaps().getInteger() - (completedLeader > completedReference ? completedLeader : completedReference);
//            if (togo > getLaps().getInteger())
//                togo = getLaps().getInteger();
            
            int togo = getLapsToGo().getInteger();

            if (togo == 2) {
                flagnames.append(";"); flagnames.append(Session.Message.TWOTOGO);
            }
            else
            if (togo == 5) {
                flagnames.append(";"); flagnames.append(Session.Message.FIVETOGO);
            }
            else
            if (togo == 10) {
                flagnames.append(";"); flagnames.append(Session.Message.TENTOGO);
            }

            flagnames.append(";");
            d.setValue(flagnames.toString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getName(String session) {
        Data d = super.getName(session);
        
        if (m_SIMPlugin.isConnected()) {
            if (session.isEmpty()) {
                int i = m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum");
                session = Integer.toString(i);
            }
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",session,"SessionName");
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public    Data    getNumberOfCarClasses() {
        Data d = super.getNumberOfCarClasses();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String count = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","NumCarClasses");
            if (!count.isEmpty()) {
                d.setValue(Integer.parseInt(count),"",Data.State.NORMAL);
            }

//for testing only            
//d.setValue(3,"",Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data    getRadioChannels() {
        Data d = super.getRadioChannels();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String count = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","NumFrequencies");
            if (!count.isEmpty()) {
                d.setValue(Integer.parseInt(count),"",Data.State.NORMAL);
            }
        }
        return d;
    }

    public    Data    getRadioChannelActive() {
        Data d = super.getRadioChannelActive();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            //String channel = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","SelectedRadioNum");
            String channel = m_SIMPlugin.getIODriver().getVars().getString("RadioTransmitFrequencyIdx");
            if (!channel.isEmpty()) {
                d.setValue(Integer.parseInt(channel),"",Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public    Data    getRadioChannelIsDeleteable(int channel) {
        Data d = super.getRadioChannelIsDeleteable(channel);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","Frequencies",Integer.toString(channel),"IsDeletable");
                d.setValue(s.equals("1"));
                d.setState(Data.State.NORMAL);
            }
        }
        return d;    
    }
    
    @Override
    public    Data    getRadioChannelIsListenOnly(int channel) {
        Data d = super.getRadioChannelIsListenOnly(channel);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","Frequencies",Integer.toString(channel),"CanSquawk");
                d.setValue(!s.equals("1"));
                d.setState(Data.State.NORMAL);
            }
        }
        return d;    
    }
    
    @Override
    public    Data    getRadioChannelIsMutable(int channel) {
        Data d = super.getRadioChannelIsMutable(channel);

//This flag doesn't work, when you mute a channel, you cannot tell if it's muted until it is the active channel.
//It will not let you mute the active channel through the chat commands, yet you can in the F10 black box        
//You also cannot tell of all the channels have been muted either.        
//I'm going to disable for now so clients won't offer a mute option. As of 11/11/2015        
//        d.setState(Data.State.OFF);
//        
//        if (m_SIMPlugin.isConnected()) {
//            int count = getRadioChannels().getInteger();
//            if (channel >= 0 && channel < count) {
//                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","Frequencies",Integer.toString(channel),"IsMutable");
//                d.setValue(s.equals("1"));
//                d.setState(Data.State.NORMAL);
//            }
//        }
        return d;    
    }
    
    @Override
    public    Data    getRadioChannelIsMuted(int channel) {
        Data d = super.getRadioChannelIsMuted(channel);
        d.setState(Data.State.OFF);
        
        //This flag doesn't work, when you mute a channel, you cannot tell if it's muted until it is the active channel.
        //You also cannot tell of all the channels have been muted either.        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count 
            && getRadioChannelActive().getInteger() == channel //TODO: Remove when iRacing fixes their code.
            ) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","Frequencies",Integer.toString(channel),"Muted");
                d.setValue(s.equals("1"));
                d.setState(Data.State.NORMAL);
            }
        }
        return d;    
    }
    
    @Override
    public    Data    getRadioChannelIsScanable(int channel) {
        Data d = super.getRadioChannelIsScanable(channel);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","Frequencies",Integer.toString(channel),"CanScan");
                d.setValue(s.equals("1"));
                d.setState(Data.State.NORMAL);
            }
        }
        return d;    
    }
    
    @Override
    public    Data    getRadioChannelName(int channel) {
        Data d = super.getRadioChannelName(channel);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","Frequencies",Integer.toString(channel),"FrequencyName");
                d.setValue(s);
                d.setState(Data.State.NORMAL);
            }
        }
        return d;    
    }

    @Override
    public    Data    getRadioScan() {
        Data d = super.getRadioScan();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","ScanningIsOn");
            if (!s.isEmpty())
                d.setValue(s.equals("1"));
            d.setState(Data.State.NORMAL);
        }
        return d;    
    }

    @Override
    public    Data    setRadioChannel(int channel) {
        Data d = super.setRadioChannel(channel);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                if (!getRadioChannelIsListenOnly(channel).getBoolean()) {
                    String name = getRadioChannelName(channel).getString();
                    d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "TRANSMIT").replace("[NAME]",name)).getString());
                    d.setState(Data.State.NORMAL);
                }
            }
        }
        return d;    
    }   
     
    @Override
    public    Data    setRadioChannelDelete(int channel) {
        Data d = super.setRadioChannelDelete(channel);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                if (getRadioChannelIsDeleteable(channel).getBoolean()) {
                    String name = getRadioChannelName(channel).getString();
                    d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "REMOVE").replace("[NAME]",name)).getString());
                    d.setState(Data.State.NORMAL);
                }
            }
        }
        return d;    
    }   
     
    @Override
    public    Data    setRadioChannelMute(int channel,boolean flag) {
        Data d = super.setRadioChannelMute(channel,flag);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                if (getRadioChannelIsMutable(channel).getBoolean()) {
                    String name = getRadioChannelName(channel).getString();
                    if (flag)
                        d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "MUTE").replace("[NAME]",name)).getString());
                    else
                        d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "UNMUTE").replace("[NAME]",name)).getString());
                    d.setState(Data.State.NORMAL);
                }
            }
        }
        return d;    
    }   
     
    @Override
    public    Data    setRadioChannelName(String channelName) {
        Data d = super.setRadioChannelName(channelName);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            int existingChannelNumber = -1;
            for (int channelNumber = 0; channelNumber < count; channelNumber++) {
                String name = getRadioChannelName(channelNumber).getString();
                if (name.equalsIgnoreCase(channelName)) {
                    existingChannelNumber = channelNumber;
                    break;
                }
            }
            
            //if it does not exist, add it and it will become the active channel
            if (existingChannelNumber == -1) {
                d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "ADD").replace("[NAME]",channelName)).getString());
                d.setState(Data.State.NORMAL);
            }
            else {
                d = setRadioChannel(existingChannelNumber);
            }
        }
        return d;    
    }   
     
    @Override
    public    Data    setRadioScan(boolean flag) {
        Data d = super.setRadioScan(flag);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            if (flag)
                d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "SCAN")).getString());
            else
                d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "NOSCAN")).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;    
    }   

    private String _getReplayText(int speed, boolean slowmotion, boolean isReplayPlaying) {
        if (!isReplayPlaying) {
            return("<>");
        }
        else
        if (speed == 1) {
            if (slowmotion)
                return(">> 1/2x");
            else {
                int frameNum = m_SIMPlugin.getIODriver().getVars().getInteger("ReplayFrameNumEnd");
                if (frameNum == 1)
                    return ">>>>>";
                return(">");
            }
        }
        else
        if (speed == 2) {
            return(">> 2x");
        }
        else
        if (speed == 3) {
            return(">> 1/4x");
        }
        else
        if (speed == 4) {
            return(">> 4x");
        }
        else
        if (speed == 7) {
            return(">> 1/8x");
        }
        else
        if (speed == 8) {
            return(">> 8x");
        }
        else
        if (speed == 11) {
            return(">> 1/12x");
        }
        else
        if (speed == 12) {
            return(">> 12x");
        }
        else
        if (speed == 15) {
            return(">> 1/16x");
        }
        else
        if (speed == 16) {
            return(">> 16x");
        }
        if (speed == -1) {
            if (slowmotion)
                return("<< 1/2x");
            else {
                return("<<");
            }
        }
        else
        if (speed == -2) {
            return("<< 2x");
        }
        else
        if (speed == -3) {
            return("<< 1/4x");
        }
        else
        if (speed == -4) {
            return("<< 4x");
        }
        else
        if (speed == -7) {
            return("<< 1/8x");
        }
        else
        if (speed == -8) {
            return("<< 8x");
        }
        else
        if (speed == -11) {
            return("<< 1/12x");
        }
        else
        if (speed == -12) {
            return("<< 12x");
        }
        else
        if (speed == -15) {
            return("<< 1/16x");
        }
        else
        if (speed == -16) {
            return("<< 16x");
        }
        else
        if (speed == 0) {
            return("||");
        }
        return "";
    }
    
    @Override
    public Data getReplay() {
        Data d = super.getReplay();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int speed = m_SIMPlugin.getIODriver().getVars().getInteger("ReplayPlaySpeed");
            boolean slowmotion = m_SIMPlugin.getIODriver().getVars().getBoolean("ReplayPlaySlowMotion");
            boolean isReplayPlaying = m_SIMPlugin.getIODriver().getVars().getBoolean("IsReplayPlaying");

            d.setValue(_getReplayText(speed,slowmotion,isReplayPlaying));
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data setReplay(String command) {
        Data d = super.setReplay(command);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int speed = m_SIMPlugin.getIODriver().getVars().getInteger("ReplayPlaySpeed");
            int slowmotion = m_SIMPlugin.getIODriver().getVars().getInteger("ReplayPlaySlowMotion");
            boolean isReplayPlaying = m_SIMPlugin.getIODriver().getVars().getBoolean("IsReplayPlaying");
    
            if (isReplayPlaying) {
                if (command.equalsIgnoreCase("PLAY") || command.equalsIgnoreCase(">")) {
                    speed = 1;
                    slowmotion = 0;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("PAUSE") || command.equalsIgnoreCase("||")) {
                    speed = 0;
                    slowmotion = 0;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("SLOWMOTION") || command.equalsIgnoreCase("SM") || command.equalsIgnoreCase("|>")) {
                    if (speed == 0)
                        speed = 1;
                    else
                    if (speed == 1)
                        speed = 3;
                    else
                    if (speed == 3)
                        speed = 7;
                    else
                    if (speed == 7)
                        speed = 11;
                    else
                    if (speed == 11)
                        speed = 15;
                    else
                    if (speed == 15)
                        speed = 15;
                    else
                    if (speed > 0)
                        speed = 1;
                    else
                    if (speed == -1)
                        speed = -3;
                    else
                    if (speed == -3)
                        speed = -7;
                    else
                    if (speed == -7)
                        speed = -11;
                    else
                    if (speed == -11)
                        speed = -15;
                    else
                    if (speed == -15)
                        speed = -15;
                    else
                    if (speed < 0)
                        speed = -1;
                    
                    slowmotion = 1;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("REWIND") || command.equalsIgnoreCase("RW") || command.equalsIgnoreCase("<<") || command.equalsIgnoreCase("<")) {
                    if (speed >= 0)
                        speed = -1;
                    else
                    if (speed == -1 && slowmotion == 0)
                        speed = -2;
                    else
                    if (speed == -2)
                        speed = -4;
                    else
                    if (speed == -4)
                        speed = -8;
                    else
                    if (speed == -8)
                        speed = -12;
                    else
                    if (speed == -12)
                        speed = -16;
                    else
                    if (speed == -16)
                        speed = -16;
                    else
                    if (speed <= 0)
                        speed = -1;
                    
                    slowmotion = 0;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("FASTFORWARD") || command.equalsIgnoreCase("FF") || command.equalsIgnoreCase(">>")) {
                    if (speed <= 0)
                        speed = 1;
                    else
                    if (speed == 1 && slowmotion == 0)
                        speed = 2;
                    else
                    if (speed == 2)
                        speed = 4;
                    else
                    if (speed == 4)
                        speed = 8;
                    else
                    if (speed == 8)
                        speed = 12;
                    else
                    if (speed == 12)
                        speed = 16;
                    else
                    if (speed == 16)
                        speed = 16;
                    else
                    if (speed >= 0)
                        speed = 1;
                    
                    slowmotion = 0;
                    d.setState(Data.State.NORMAL);
                }
                else {
                    command = "invalid";
                    d.setState(Data.State.ERROR);
                }
                
                Server.logger().info(String.format("ReplaySetPlaySpeed(%s,speed=%s,slowmotion=%s",command,speed,slowmotion));
                
                BroadcastMsg.send(m_SIMPlugin.getIODriver(), "ReplaySetPlaySpeed",Integer.toString(speed),Integer.toString(slowmotion));
                
                d.setValue(_getReplayText(speed,slowmotion==1,isReplayPlaying));
            }
        }        
        return d;
    }

    @Override
    public Data setReplayPosition(String command) {
        Data d = super.setReplayPosition(command);
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            //int speed = m_SIMPlugin.getIODriver().getVars().getInteger("ReplayPlaySpeed");
            //int slowmotion = m_SIMPlugin.getIODriver().getVars().getInteger("ReplayPlaySlowMotion");
            boolean isReplayPlaying = m_SIMPlugin.getIODriver().getVars().getBoolean("IsReplayPlaying");
            int frame = 0; //m_SIMPlugin.getIODriver().getVars().getInteger("ReplayFrameNum");
            int mode = -1;
            String message = "";
            
            if (isReplayPlaying) {
                if (command.equalsIgnoreCase("BEGINNING") || command.equalsIgnoreCase("START")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_ToStart;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("ENDING") || command.equalsIgnoreCase("END")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_ToEnd;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("NEXTFRAME") || command.equalsIgnoreCase("NEXT")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_NextFrame;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("PREVFRAME") || command.equalsIgnoreCase("PREV")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_PrevFrame;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("NEXTLAP")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_NextLap;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("PREVLAP")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_PrevLap;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("NEXTCRASH")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_NextIncident;
                    d.setState(Data.State.NORMAL);
                    setReferenceCar("CRASHES");
                }
                else
                if (command.equalsIgnoreCase("PREVCRASH")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_PrevIncident;
                    d.setState(Data.State.NORMAL);
                    setReferenceCar("CRASHES");
                }
                else
                if (command.equalsIgnoreCase("NEXTSESSION")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_NextSession;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("PREVSESSION")) {
                    message = "ReplaySearch";
                    mode = BroadcastMsg.RpySrchMode.RpySrch_PrevSession;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("FORWARD15")) {
                    message = "ReplaySetPlayPosition";
                    mode = BroadcastMsg.RpyPosMode.RpyPos_Current;
                    frame = 60 * 15;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("BACKWARD15")) {
                    message = "ReplaySetPlayPosition";
                    mode = BroadcastMsg.RpyPosMode.RpyPos_Current;
                    frame = 60 * -15;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("FORWARD30")) {
                    message = "ReplaySetPlayPosition";
                    mode = BroadcastMsg.RpyPosMode.RpyPos_Current;
                    frame = 60 * 30;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("BACKWARD30")) {
                    message = "ReplaySetPlayPosition";
                    mode = BroadcastMsg.RpyPosMode.RpyPos_Current;
                    frame = 60 * -30;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("FORWARD60")) {
                    message = "ReplaySetPlayPosition";
                    mode = BroadcastMsg.RpyPosMode.RpyPos_Current;
                    frame = 60 * 60;
                    d.setState(Data.State.NORMAL);
                }
                else
                if (command.equalsIgnoreCase("BACKWARD60")) {
                    message = "ReplaySetPlayPosition";
                    mode = BroadcastMsg.RpyPosMode.RpyPos_Current;
                    frame = 60 * -60;
                    d.setState(Data.State.NORMAL);
                }
                else {
                    command = "invalid";
                    d.setState(Data.State.ERROR);
                }
                
                if (mode > -1) {
                    Server.logger().info(String.format("%s(%s,mode=%d,frame=%d",message,command,mode,frame));
                
                    BroadcastMsg.send(m_SIMPlugin.getIODriver(), message,Integer.toString(mode),Integer.toString(frame));
                }
                
                d.setValue(command.toUpperCase());
            }
        }        
        return d;
    }
    
    @Override
    public Data getStartTime() {
        Data d = super.getStartTime();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int sessionNum = this.m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum");
            String sessionNumString = this.m_SIMPlugin.getIODriver().getVars().getString("SessionNum");
            String timezone = m_track.getTimeZone().getString();
            String timezoneShort =  this._getShortTimeZone(new Date(d.getLong() * 1000L), timezone);
            String timezoneOffset = d.getState();
            boolean addPreviousSessions = false;
            
            //"TimeOfDay": "2:00 pm"
            String timeOfDay = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNumString,"TimeOfDay");

            //if not in the session data, get the global data
            if (timeOfDay.isEmpty())
                timeOfDay    = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","TimeOfDay");
            
            //"Date": "2018-11-14"
            String date      = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNumString,"Date");
            
            //if not in the session data, get the global data
            if (date.isEmpty()) {
                date         = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","Date");
                addPreviousSessions = true;
            }
            
            Calendar sessionCal    = Calendar.getInstance(TimeZone.getTimeZone(timezone));
            sessionCal.setTimeInMillis(d.getLong()*1000L);
            
            if (!date.isEmpty()) {
                if (date.length() == 10) {
                    try {
                        sessionCal.set(Calendar.YEAR,Integer.parseInt(date.substring(0, 4)));
                        sessionCal.set(Calendar.MONTH,Integer.parseInt(date.substring(5, 7))-1);
                        sessionCal.set(Calendar.DAY_OF_MONTH,Integer.parseInt(date.substring(8, 10)));
                    }
                    catch (Exception e) {} //ignore any parsing issues
                }
            }
            
            if (!timeOfDay.isEmpty()) {
                if (timeOfDay.length() == 7 || timeOfDay.length() == 8) {
                    String parts[] = timeOfDay.split("[: ]");
                    if (parts.length == 3) {
                        //remove any time element
                        sessionCal.clear(Calendar.AM_PM);
                        sessionCal.clear(Calendar.HOUR);
                        sessionCal.clear(Calendar.HOUR_OF_DAY);
                        sessionCal.clear(Calendar.MINUTE);
                        sessionCal.clear(Calendar.SECOND);
                        sessionCal.clear(Calendar.MILLISECOND);
                        sessionCal.clear(Calendar.DST_OFFSET);
                        
                        int hours = Integer.parseInt(parts[0]);
                        int minutes = Integer.parseInt(parts[1]);
                        Boolean pm = parts[2].equalsIgnoreCase("PM");
                        
                        try {
                            sessionCal.setTimeZone(TimeZone.getTimeZone(timezone));
                            sessionCal.add(Calendar.HOUR_OF_DAY, pm && hours < 12 ? hours + 12 : hours);
                            sessionCal.add(Calendar.MINUTE, minutes);
                        }
                        catch (Exception e) {} //ignore any parsing issues
                        timezoneShort = this._getShortTimeZone(new Date(sessionCal.getTimeInMillis()), timezone);
                        timezoneOffset = this._getTimeZoneOffset(new Date(sessionCal.getTimeInMillis()), timezone);
                    }
                }

                double sessionTimeUTC = (sessionCal.getTimeInMillis() / 1000L);
                
                //if we had to use the global time then
                //Calculate the start of the current session by adding the time from completed sessions.
                //TODO: what if the admin advances the previous sessions before they complete?
                //      I've also seen long qual sessions of 20 minutes needing to add 3 minutes
                for (int session=0; addPreviousSessions && session < sessionNum;session++) {
                    String sessionTime = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",String.format("%d", session),"SessionTime");
                    if (!sessionTime.equals("unlimited")) {
                        String time[] = sessionTime.split("[ ]");
                        if (time.length > 0 && !time[0].isEmpty()) {
                            sessionTimeUTC += Double.parseDouble(time[0]);
                            //iRacing is adding time between sessions
                            if (m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",String.format("%d", session),"SessionType").equals("Practice")) {
                                sessionTimeUTC += 120;
                            }
                            else
                            if (m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",String.format("%d", session),"SessionType").equals("Open Qualify")) {
                                sessionTimeUTC += 300;
                            }
                            else
                            if (m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",String.format("%d", session),"SessionType").equals("Lone Qualify")) {
                                sessionTimeUTC += 300;
                            }
                        }
                    }
                }
                
    //            Server.logger().finest(String.format("%tc",(long)Math.floor(sessionTimeUTC*1000)));
                
                d.setValue(sessionTimeUTC < 0.0 ? 0.0 : sessionTimeUTC);
            }
            d.setUOM("s");
//            d.setState(timezoneShort);
            d.setState(timezoneOffset);  //Offsets are much more reliable than short time zones. Too many conflicts.
        }
        return d;
    }

    @Override
    public Data getStatus() {
        Data d = super.getStatus();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            if (this.getIsCheckeredFlag().getBoolean())
                d.setValue(Status.FINISHED);
            else
            if (this.getIsWhiteFlag().getBoolean())
                d.setValue(Status.GREEN);
            else
            if (this.getIsCautionFlag().getBoolean())
                d.setValue(Status.CAUTION);
            else
            if (this.getIsRedFlag().getBoolean())
                d.setValue(Status.RED);
            else
            if (getType().equals(Type.LONE_QUALIFY)
            ||  getType().equals(Type.OPEN_QUALIFY)
            ||  getType().equals(Type.RACE)
            ) {
                if (m_seenGreen)
                    d.setValue(Status.GREEN);
                else
                    d.setValue(m_status);
            }
            else {
                d.setValue(Session.Status.GREEN);
            }
            m_status = d.getString();
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getStrengthOfField() {
        Data d = super.getStrengthOfField();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            d.setValue(m_cars.getSOF());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getTime() {
        Data d = super.getTime();
        if (m_SIMPlugin.isConnected()) {
            int sessionTimeOfDay = (int) Math.floor(m_SIMPlugin.getIODriver().getVars().getDouble("SessionTimeOfDay"));
            if (sessionTimeOfDay >= 0.0) {
                String timezone = m_track.getTimeZone().getString();
                Calendar sessionCal    = Calendar.getInstance(TimeZone.getTimeZone(timezone));
                sessionCal.setTimeInMillis(getStartTime().getLong()*1000L);

                long startTime = (sessionCal.get(Calendar.HOUR_OF_DAY) * 60 * 60)
                               + (sessionCal.get(Calendar.MINUTE) * 60)
                               + (sessionCal.get(Calendar.SECOND));
                
                //remove any time element
                sessionCal.clear(Calendar.AM_PM);
                sessionCal.clear(Calendar.HOUR);
                sessionCal.clear(Calendar.HOUR_OF_DAY);
                sessionCal.clear(Calendar.MINUTE);
                sessionCal.clear(Calendar.SECOND);
                sessionCal.clear(Calendar.MILLISECOND);
                
                //adjust for 24 hour races, or races that cross midnight
                if (sessionTimeOfDay < startTime) {
                    sessionCal.set(Calendar.SECOND,(24*60*60) + sessionTimeOfDay);
                }
                else {
                    sessionCal.set(Calendar.SECOND,sessionTimeOfDay);
                }
                d.setValue(sessionCal.getTimeInMillis() / 1000L);
            }
//            else {
//                String sFactor = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","EarthRotationSpeedupFactor");
//                double factor = Server.getArg("simtime-multiplier",1.0);
//                if (!sFactor.isEmpty())
//                    factor = Integer.parseInt(sFactor);
//                
//                d.setValue( d.getDouble() * factor );
//            }
        }
        return d;
    }

    @Override
    public Data getTimeElapsed() {
        Data d = super.getTimeElapsed();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            double sessiontime = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTime");
            d.setValue(sessiontime < 0.0 ? 0.0 : sessiontime);
            d.setUOM(m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("SessionTime").Unit);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getTimeRemaining() {
        Data d = super.getTimeRemaining();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            double sessionTimeRemain = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTimeRemain");
            double estimatedTimeRemain = 0.0;
            double sessionTime = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTime");
            
            //used for testing. Set the arg to the duration of the session in seconds
            sessionTimeRemain = Math.min(sessionTimeRemain,Server.getArg("iracing-sessionduration",sessionTimeRemain + sessionTime) - sessionTime); //for testing
            
            d.setUOM(m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("SessionTimeRemain").Unit);
            
            //if the session has limited laps, then estimate the time remaining
            //based on the overall leader
            if (getLaps().getInteger() != Session.UNLIMITEDLAPS) {
                Car leader                           = m_SIMPlugin.getSession().getCar(CarIdentifiers.LEADER);
                ArrayList<Double> lapTimesLeader     = leader.getLapTimes().getDoubleArray();
                ArrayList<Boolean> lapsInvalidLeader = leader.getLapInvalidFlags().getBooleanArray();
                double timeLeader                    = 0.0;
                double minTimeLeader                 = 9999.0;
                int count = 0;
                int lap;
                
                //get the minimum time for the leader
                for (lap = lapTimesLeader.size() - 1; lap >= 0 && count < 10; lap--) {
                    if (lap < lapsInvalidLeader.size() && !lapsInvalidLeader.get(lap) && lapTimesLeader.get(lap) > 0.0 && lapTimesLeader.get(lap) < minTimeLeader ) {
                        minTimeLeader = lapTimesLeader.get(lap);
                    }
               }
                
                //get the average lap time for the leader's last 10 laps
                for (lap = lapTimesLeader.size() - 1; lap >= 0 && count < 10; lap--) {
                    if (lap < lapsInvalidLeader.size() && !lapsInvalidLeader.get(lap) && lapTimesLeader.get(lap) > 0.0 && lapTimesLeader.get(lap) < (minTimeLeader*1.5)) {
                        timeLeader += lapTimesLeader.get(lap);
                        count++;
                    }
                }
                
                if (count > 0) {
                    timeLeader /= count;    //convert to an average.
                    estimatedTimeRemain = timeLeader * (leader.getLapsToGo().getDouble() - (leader.getLap(LapType.COMPLETEDPERCENT).getDouble() / 100.0));
                }
            }
            
            if (sessionTimeRemain <= 0.0 || sessionTimeRemain >= 604800.0) {
                d.setValue(Math.max(0.0,estimatedTimeRemain),"~"+d.getUOM());
            }
            else
            if (estimatedTimeRemain > 0.0 && sessionTimeRemain > 0.0) {
                if (estimatedTimeRemain < sessionTimeRemain)
                    d.setValue(estimatedTimeRemain,"~"+d.getUOM());
                else
                    d.setValue(sessionTimeRemain);
            }
            else
                d.setValue(Math.max(0.0,sessionTimeRemain));
            
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getType() {
        Data d = super.getType();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            int session = m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum");
            d.setValue(m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(session),"SessionType").toUpperCase());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public    Data setAdvanceFlag() {
        Data d = super.setAdvanceFlag();
        
        d.setValue(setChat(this.getSendKeys("ADMIN_COMMANDS", "ADVANCE")).getString());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override    
    public    Data setChat(String text) {
        Data d = super.setChat(text);

        //Need to make iRacing is focused, then send the keys.
        Windows.setForegroundWindow(null,Server.getArg("iracing-title",IRACING_TITLE));
            //get into chat mode.
            //SendKeys.sendKeys("{DELAY 500}{ESCAPE}t{DELAY 100}");
            m_SIMPlugin.getIODriver().broadcastMsg(BroadcastMsg.BroadcastChatComand,BroadcastMsg.ChatCommandMode.ChatCommand_BeginChat,0);
            SendKeys.delay(Integer.parseInt(Server.getArg("iracing-chatmodedelay","200")));
        
            String sentText = this.getSendKeys("CHAT", "ALL").replace("[TEXT]", text);
            if (Server.getArg("sendkeys-testmode",false))
                sentText = "testmode="+sentText;
            SendKeys.sendKeys(sentText);
            d.setValue("ChatSent: " + sentText);
            d.setState(Data.State.NORMAL);
            Server.logger().info(d.getString());
        return d;
    }
    
    @Override    
    public    Data setChatReply(String text) {
        Data d = super.setChatReply(text);
        d.setState(Data.State.OFF);

        //Need to make iRacing is focused, then send the keys.
        Windows.setForegroundWindow(null,Server.getArg("iracing-title",IRACING_TITLE));        
            //get into chat reply mode.
            //SendKeys.sendKeys("{DELAY 500}{ESCAPE}r{DELAY 100}");
            m_SIMPlugin.getIODriver().broadcastMsg(BroadcastMsg.BroadcastChatComand,BroadcastMsg.ChatCommandMode.ChatCommand_Reply,0);
            SendKeys.delay(Integer.parseInt(Server.getArg("iracing-chatmodedelay","200")));
        
            String sentText = this.getSendKeys("CHAT", "REPLY").replace("[TEXT]", text);
            if (Server.getArg("sendkeys-testmode",false))
                sentText = "testmode="+sentText;
            SendKeys.sendKeys(sentText);
            d.setValue("ChatReplySent: " + sentText);
            d.setState(Data.State.NORMAL);
            Server.logger().info(d.getString());
        return d;
    }
    
    @Override
    public    Data setChatFlag(boolean onOffFlag) {
        Data d = super.setChatFlag(onOffFlag);
        
        if (onOffFlag) {
            d.setValue(setChat(this.getSendKeys("ADMIN_COMMANDS", "CHAT").replace("[DRIVER]", "")).getString());
        }
        else {
            d.setValue(setChat(this.getSendKeys("ADMIN_COMMANDS", "NCHAT").replace("[DRIVER]", "")).getString());
        }
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public    Data setCamera(String cameraName,String focusOn,String carIdentifier) {
        Data d = super.setCamera(cameraName,focusOn,carIdentifier);
        d.setState(Data.State.OFF);
        
        
        if (_loadCameras()) {
            
            _camera camera  = m_camera_groups.get(cameraName.toLowerCase());
            
            String carNumber = "";
            
            if (camera != null) {
                
                String csMode  = null;
                
                if (focusOn.equalsIgnoreCase("CRASHES")) {
                    csMode = Integer.toString(BroadcastMsg.csMode.csFocusAtIncident);                    
                    //we have to keep track of this since it is not in the telemetry
                    m_cameraFocusOn = focusOn.toUpperCase();
                }
                else
                if (focusOn.equalsIgnoreCase("DRIVER")) {
                    iRacingCar car  = (iRacingCar) getCar(carIdentifier);
                    if (car != null) {
                        String status        = car.getStatus().getString();
 
//if you do this, you cannot change to drivers that have already left the session
//TODO: How to tell the difference between loaded replay and in session replay                        
//                        if (status.equals(Car.Status.INVALID)) {
//                            Server.logger().info("Cannot change Camera to #"+car.getNumber().getString()+". Not on the track.");
//                            return getCamera();
//                        }
                        
                        csMode = Integer.toString(BroadcastMsg.csMode.csFocusAtDriver + car.getNumberRaw());
                        carNumber = "#"+car.getNumber().getString();
                    }
                    else {
                        Server.logger().info("Cannot change Camera, no driver has focus.");
                        return getCamera();
                    }
                    //we have to keep track of this since it is not in the telemetry
                    m_cameraFocusOn = focusOn.toUpperCase();
                }
                else
                if (focusOn.equalsIgnoreCase("EXCITING")) {
                    csMode = Integer.toString(BroadcastMsg.csMode.csFocusAtExciting);
                    //we have to keep track of this since it is not in the telemetry
                    m_cameraFocusOn = focusOn.toUpperCase();
                }
                else {  //default will be to focus on the leader.
                    csMode = Integer.toString(BroadcastMsg.csMode.csFocusAtLeader);
                    //we have to keep track of this since it is not in the telemetry
                    m_cameraFocusOn = "LEADER";
                }

                d.setState(Data.State.NORMAL);
                
                Server.logger().info(String.format("Switching Camera to %s,%s,%s (%d)",m_cameraFocusOn,carNumber,camera.name,camera.groupNumber));
                
                //iRacing igores the 3rd argument, the camera number in the group. 
                //instead it will cycle between all cameras in the group
                //therefore, it doesn't really matter what camera I send it, so I will send it #1.
                BroadcastMsg.send(m_SIMPlugin.getIODriver(), "CamSwitchNum",csMode,Integer.toString(camera.groupNumber),"1");
            }            
        }
        
        return d;
    }

//    @Override
//    public    Data setCamera(String carIdentifier, String group, String camera) {
//        Data d = super.setCamera(carIdentifier,group,camera);
//        
//        if (m_SIMPlugin.isConnected()) {
//            iRacingCar car              = (iRacingCar) getCar(carIdentifier);
//            
//            if (car != null) {
//                int CamCarIdx        = m_SIMPlugin.getIODriver().getVars().getInteger("CamCarIndex");
//                int CamCameraNumber  = m_SIMPlugin.getIODriver().getVars().getInteger("CamCameraNumber");
//                int CamGroupNumber   = m_SIMPlugin.getIODriver().getVars().getInteger("CamGroupNumber");
//                String currentGroup  = m_SIMPlugin.getIODriver().getSessionInfo().getString("CameraInfo","Groups",Integer.toString(CamGroupNumber-1),"GroupName");
//                String currentCamera = m_SIMPlugin.getIODriver().getSessionInfo().getString("CameraInfo","Groups",Integer.toString(CamGroupNumber-1),"Cameras",Integer.toString(CamCameraNumber-1),"CameraName");
//                String status        = car.getStatus().getString();
//                
//                if (status.equals(Car.Status.INVALID)) {
//                    Server.logger().info("Cannot change Camera to #"+car.getNumber().getString()+". Not on the track.");
//                }
//                else {
//                    d.setValue(currentGroup + "/" + currentCamera,"Camera",Data.State.NORMAL);
//                    
//                    String csMode = Integer.toString(carIdentifier.equalsIgnoreCase("LEADER") || carIdentifier.equalsIgnoreCase("LEADERCLASS") 
//                                  ? BroadcastMsg.csMode.csFocusAtLeader 
//                                  : BroadcastMsg.csMode.csFocusAtDriver + car.getNumberRaw());
//                    
//                    //check if the current camera is different from the requested.
//                    String newCamGroupNumber  = Integer.toString(CamGroupNumber);    //default to the current group
//                    String newCamCameraNumber = Integer.toString(CamCameraNumber);   //default to the current camera
//                    
//                    //TODO: lookup the group and camera numbers
//                    
//                    Server.logger().info("Changing Camera to #"+car.getNumber().getString()+"(raw="+csMode+",carIdx="+car.getId().getString()+",status="+status+") - "+d.getString());
//                
//                    BroadcastMsg.send(m_SIMPlugin.getIODriver(), "CamSwitchNum",csMode,newCamGroupNumber,newCamCameraNumber);
//                }
//            }            
//        }
//        
//        return d;
//    }
    
    @Override
    public    Data setCautionFlag() {
        Data d = super.setCautionFlag();
        
        d.setValue(setChat(this.getSendKeys("ADMIN_COMMANDS", "YELLOW")).getString());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override    
    public    Data setReloadPaint() {
        Data d = super.setReloadPaint();

        if (m_SIMPlugin.isConnected()) {
            m_SIMPlugin.getIODriver().broadcastMsg(BroadcastMsg.BroadcastReloadTextures,ReloadTexturesMode.ReloadTextures_All,0);
            d.setValue("Reloading All Textures (i.e. Paints)");
            d.setState(Data.State.NORMAL);
            Server.logger().info(d.getString());
        }
        return d;
    }

    public boolean processData() {
//        if (m_cache.init()) { //returns true if the cache is initialized. Will do this on new sessions.
        if (m_sessionUniqueID != m_SIMPlugin.getIODriver().getVars().getInteger("SessionUniqueID")
        ||  m_sessionVersion   > m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate()
        ||  m_dataVersion      > m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
        ||  m_connected       != ((m_SIMPlugin.getIODriver().getHeader().getStatus() & StatusField.stConnected) != 0)
        ) {
            /**
            //values from charlotte.bin
            //            header_ver=(0x00000001)
            //            header_status(0x00000001)
            //            header_tickRate(60)
            //            header_sessionInfoUpdate(29)
            //            header_sessionInfoLen(131072)
            //            header_sessionInfoOffset(112)
            //            header_numVars(94)
            //            header_varHeaderOffset(131184)
            //            header_numBuf(3)
            //            header_bufLen(1930)
            //            header_varBuf[0].tickcount=10923
            //            header_varBuf[0].bufOffset=721008
            //            header_varBuf[1].tickcount=10924
            //            header_varBuf[1].bufOffset=745584
            //            header_varBuf[2].tickcount=10922
            //            header_varBuf[2].bufOffset=770160
            //            header_Latest_varBufTick=10924
            //            header_Latest_varBuf=1
            String s = m_SIMPlugin.getIODriver().getHeader().toJson();
            System.err.printf("%s",s);
            /**/

            /**
            if (m_SIMPlugin.getIODriver().getVars().getInteger("SessionUniqueID") == 0) {
                VarHeaders varHeaders = m_SIMPlugin.getIODriver().getVarHeaders();
                for(String var : varHeaders.getVarHeaders().keySet()) {
                    VarHeaders.VarHeader varHeader = varHeaders.getVarHeader(var);
                    for (int i=0; i < varHeader.Count; i++) {
                        if (i == 0) {
                            System.err.printf("\t%s(%s)=", varHeader.Name, varHeader.Unit);
                        }
                        if (varHeader.Count > 1 && varHeader.Type.getType() != VarType.irsdk_char) {
                            System.err.printf(",[%d]=", i);
                        }
                        try {
                            if (varHeader.Type.getType() == VarType.irsdk_bitField) {
                                System.err.printf("0x%X", m_SIMPlugin.getIODriver().getVars().getBitfield(varHeader.Name,i));
                            }
                            else
                            if (varHeader.Type.getType() == VarType.irsdk_bool) {
                                System.err.printf("%d", m_SIMPlugin.getIODriver().getVars().getInteger(varHeader.Name,i));
                            }
                            else
                            if (varHeader.Type.getType() == VarType.irsdk_char && i == 0) {
                                System.err.printf("%s", m_SIMPlugin.getIODriver().getVars().getString(varHeader.Name));
                            }
                            else
                            if (varHeader.Type.getType() == VarType.irsdk_double) {
                                System.err.printf("%f", m_SIMPlugin.getIODriver().getVars().getDouble(varHeader.Name,i));
                            }
                            else
                            if (varHeader.Type.getType() == VarType.irsdk_float) {
                                System.err.printf("%f", m_SIMPlugin.getIODriver().getVars().getFloat(varHeader.Name,i));
                            }
                            else
                            if (varHeader.Type.getType() == VarType.irsdk_int) {
                                System.err.printf("%d", m_SIMPlugin.getIODriver().getVars().getInteger(varHeader.Name,i));
                            }
                        }
                        catch (IndexOutOfBoundsException e) {
                            Server.logStackTrace(Level.WARNING,e);
                        }
                    }
                    System.err.printf("%n");
                }
                System.err.printf("---------------------%n");
            }
            /**/
            Server.logger().fine("initializing cache");
            Server.logger().fine(String.format("%d != %d,%d > %d,%d > %d"
                , m_sessionUniqueID, m_SIMPlugin.getIODriver().getVars().getInteger("SessionUniqueID")
                , m_sessionVersion,  m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate()
                , m_dataVersion,     m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
            ));
            
            _init();
            m_sessionUniqueID = m_SIMPlugin.getIODriver().getVars().getInteger("SessionUniqueID");
            m_connected       = (m_SIMPlugin.getIODriver().getHeader().getStatus() & StatusField.stConnected) != 0;
        }

        //here we let the driver give commands to support play back of recorded commands
        //normally the commands are coming from the user
        String cmdstring = m_SIMPlugin.getIODriver().getCommand();
        if (!cmdstring.isEmpty()) {
            try {
                m_SIMPlugin.getData(cmdstring);  //just pass it back in
            } catch (SIMPluginException e) {
                Server.logStackTrace(Level.SEVERE,"SIMPluginException",e);
            }
            return true;
        }

//Uncomment these to see the data while debugging. It is best to leave them commented out to increase performance.
        //String sessioninfo = m_SIMPlugin.getIODriver().getSessionInfo().toString();
        //String vars = m_SIMPlugin.getIODriver().getVars().toString();
        int m_lastSessionVersion = m_sessionVersion;

        if (m_sessionVersion != m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate()) {
            m_sessionVersion = m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate();

        } //end session version changed

        //Add cache calculations here if the data version has changed.
        if (m_dataVersion != m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()) {
            
            if (Server.getArg("log-packetloss",true) && (m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick() - m_dataVersion) > 30) {
                Server.logger().fine(String.format("PacketLoss above threshold(%d) at %d-%d: last: %d-%d, lost: %d",
                        30,
                        m_sessionVersion, m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick(),
                        m_lastSessionVersion, m_dataVersion,
                        m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick() - m_dataVersion - 1L
                ));
            }
            m_dataVersion = m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick();

            if (Server.getArg("reference-camera", true) && m_SIMPlugin.getIODriver().getVars().getBoolean("IsReplayPlaying")) {
                Integer idx = m_SIMPlugin.getIODriver().getVars().getInteger("CamCarIdx");
                if (idx > 0)
                    m_SIMPlugin.getSession().setReferenceCar("I"+idx.toString());
            }

            //when the session changes, set the initial status to engines started.
            //it will stay that way until it changes.
            if (m_previousSessionNumber != m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum"))
                m_status = Status.ENGINES_STARTED;

            //help out the getStatus() method by tracking when track is green.
            if (this.getIsCautionFlag().getBoolean()
            ||  this.getIsRedFlag().getBoolean()
            ||  this.getIsCheckeredFlag().getBoolean()
            ||  m_previousSessionNumber != m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum")
            ) {
                m_seenGreen = false;
            }
            else
            if (this.getIsGreenFlag().getBoolean()) {
                m_seenGreen = true;
            }

            m_previousSessionNumber = m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum");
            m_cars.onDataVersionChange();
            
        } //end of data version changed
              
        return true;
    }
}
