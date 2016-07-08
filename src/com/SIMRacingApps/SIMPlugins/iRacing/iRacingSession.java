package com.SIMRacingApps.SIMPlugins.iRacing;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
import com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache.*;
import com.SIMRacingApps.Util.FindFile;
import com.SIMRacingApps.Util.SendKeys;

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
    private boolean                                  m_connected = false;
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
    
    private void _init() {
        m_sessionUniqueID                = -1;
        m_dataVersion                    = -1;
        m_sessionVersion                 = -1;
        m_connected                      = false;
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

    @Override
    public Track getTrack() {
        return m_track;
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
        if (m_SIMPlugin.isConnected() && _CarIdx("LEADER") > -1) {
            d.setValue(m_cars.getCar(_CarIdx("LEADER")).getLap(Car.LapType.CAUTION).getInteger());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getCautions() {
        Data d = super.getCautions();
        if (m_SIMPlugin.isConnected() && _CarIdx("LEADER") > -1) {
            d.setValue(m_cars.getCar(_CarIdx("LEADER")).getCautions().getInteger());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getClassNames() {
        Data d = super.getClassNames();
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
    public Data getIncidentLimit() {
        Data d = super.getIncidentLimit();
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
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.green) > 0)
                d.setValue(true);
        }
        return d;
    }
    
    @Override
    public Data getIsCautionFlag() {
        Data d = super.getIsCautionFlag();
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.caution) > 0 || (flags & SessionFlags.cautionWaving) > 0)
                d.setValue(true);
        }
        return d;
    }

    @Override
    public Data getIsCheckeredFlag() {
        Data d = super.getIsCheckeredFlag();
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.checkered) > 0
            &&  (getType().equals(Session.Type.RACE) || getType().equals(Session.Type.LONE_QUALIFY))
            )
                d.setValue(true);
        }
        return d;
    }
    
    @Override
    public Data getIsCrossedFlag() {
        Data d = super.getIsCrossedFlag();
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
        }
        return d;
    }
    
    @Override
    public Data getIsWhiteFlag() {
        Data d = super.getIsWhiteFlag();
        if (m_SIMPlugin.isConnected()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.white) > 0)
                d.setValue(true);
        }
        return d;
    }
    
    @Override
    public Data getIsRedFlag() {
        Data d = super.getIsRedFlag();
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
        }
        return d;
    }
    
    @Override
    public Data getLap() {
        Data d = super.getLap();
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
            if ((flags & SessionFlags.greenHeld) > 0)     { flagnames.append(";"); flagnames.append(Session.Message.GREENHELD); }
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
    public    Data    getNumberOfCarClasses() {
        Data d = super.getNumberOfCarClasses();
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
        if (m_SIMPlugin.isConnected()) {
            int count = getRadioChannels().getInteger();
            if (channel >= 0 && channel < count) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios","0","Frequencies",Integer.toString(channel),"FrequencyName");
                d.setValue(s.startsWith("@") ? s.substring(1) : s);
                d.setState(Data.State.NORMAL);
            }
        }
        return d;    
    }

    @Override
    public    Data    getRadioScan() {
        Data d = super.getRadioScan();
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
        if (m_SIMPlugin.isConnected()) {
            if (flag)
                d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "SCAN")).getString());
            else
                d.setValue(setChat(this.getSendKeys("RADIO_COMMANDS", "NOSCAN")).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;    
    }   
     
    @Override
    public Data getStartTime() {
        Data d = super.getStartTime();
        if (m_SIMPlugin.isConnected()) {
            double sessiontime = m_SIMPlugin.getIODriver().getHeader().getSubHeader().getSessionStartDate();
            d.setValue(sessiontime < 0.0 ? 0.0 : sessiontime);
            d.setUOM("s");
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getStrengthOfField() {
        Data d = super.getStrengthOfField();
        if (m_SIMPlugin.isConnected()) {
            d.setValue(m_cars.getSOF());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getTimeElapsed() {
        Data d = super.getTimeElapsed();
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
        if (m_SIMPlugin.isConnected()) {
            double sessionTimeRemain = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTimeRemain");
            double sessionTime = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTime");
            
            //used for testing. Set the arg to the duration of the session in seconds
            sessionTimeRemain = Math.min(sessionTimeRemain,Server.getArg("iracing-sessionduration",sessionTimeRemain + sessionTime) - sessionTime); //for testing
            
            d.setUOM(m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("SessionTimeRemain").Unit);
            
            //when the session isn't timed, iRacing returns 604800 (number of seconds in a week)
            //I don't want this stupid number, set it to zero.
            if (sessionTimeRemain >= 604800.0) {
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
                    sessionTimeRemain = timeLeader * (leader.getLapsToGo().getDouble() - (leader.getLap(LapType.COMPLETEDPERCENT).getDouble() / 100.0));
                    d.setUOM("~s");
                }
                else
                    sessionTimeRemain = 0.0;
                
            }
            d.setValue((sessionTimeRemain < 0.0 || sessionTimeRemain == 604800.0) ? 0.0 : sessionTimeRemain);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getType() {
        Data d = super.getType();
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
//        if (Windows.setForegroundWindow(null,Server.getArg("iracing-title",IRACING_TITLE))) {
            //get into chat mode.
            //SendKeys.sendKeys("{DELAY 500}{ESCAPE}t{DELAY 100}");
            m_SIMPlugin.getIODriver().broadcastMsg(BroadcastMsg.BroadcastChatComand,BroadcastMsg.ChatCommandMode.ChatCommand_BeginChat,0);
            SendKeys.delay(Integer.parseInt(Server.getArg("iracing-chatmodedelay","200")));
        
            String sentText = this.getSendKeys("CHAT", "ALL").replace("[TEXT]", text);
            SendKeys.sendKeys(sentText);
            d.setValue("ChatSent: " + sentText);
            d.setState(Data.State.NORMAL);
            Server.logger().info(d.getString());
//        }
        return d;
    }
    
    @Override    
    public    Data setChatReply(String text) {
        Data d = super.setChatReply(text);

        //Need to make iRacing is focused, then send the keys.
        if (Windows.setForegroundWindow(null,Server.getArg("iracing-title",IRACING_TITLE))) {        
            //get into chat reply mode.
            //SendKeys.sendKeys("{DELAY 500}{ESCAPE}r{DELAY 100}");
            m_SIMPlugin.getIODriver().broadcastMsg(BroadcastMsg.BroadcastChatComand,BroadcastMsg.ChatCommandMode.ChatCommand_Reply,0);
            SendKeys.delay(Integer.parseInt(Server.getArg("iracing-chatmodedelay","200")));
        
            String sentText = this.getSendKeys("CHAT", "REPLY").replace("[TEXT]", text);
            SendKeys.sendKeys(sentText);
            d.setValue("ChatReplySent: " + sentText);
            d.setState(Data.State.NORMAL);
            Server.logger().info(d.getString());
        }
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
    public    Data setCautionFlag() {
        Data d = super.setCautionFlag();
        
        d.setValue(setChat(this.getSendKeys("ADMIN_COMMANDS", "YELLOW")).getString());
        d.setState(Data.State.NORMAL);
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

            m_cars.onDataVersionChange();
            
        } //end of data version changed
              
        return true;
    }
}
