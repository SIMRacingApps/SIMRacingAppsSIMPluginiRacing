package com.SIMRacingApps.SIMPlugins.iRacing;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Gauge;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Session;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugin.SIMPluginException;
import com.SIMRacingApps.SIMPlugins.iRacing.Gauges.*;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.SIMPlugins.iRacing.SessionFlags;
import com.SIMRacingApps.SIMPlugins.iRacing.TrackSurface;
import com.SIMRacingApps.SIMPlugins.iRacing.BroadcastMsg.ReloadTexturesMode;
import com.SIMRacingApps.SIMPlugins.iRacing.VarHeaders.VarHeader;
import com.SIMRacingApps.Session.Type;
import com.SIMRacingApps.Util.FindFile;
import com.SIMRacingApps.Util.State;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class iRacingCar extends Car {
    
    private final double ENTER_PIT_DELAY   = Server.getArg("pit-entering-delay",1.0);  //seconds to wait when entering pits before we're sure they stopped and did not over shoot the pit stall
                                                   //this was set to 2.0, but the Dallara was sending the tire readings before that
    private final double EXIT_PIT_DELAY    = Server.getArg("pit-exiting-delay",1.0);  //seconds to wait to confirm we have exited the pits long enough before resetting the pit black boxes and updating the historical values.
    private final double RESET_PIT_DELAY   = 0.3;  //seconds to wait after leaving pit road to send pit commands
    private final double INVALID_INPITSTALL= Server.getArg("pit-invalid-duration",1.0);  //seconds to wait before declaring invalid while in pit stall
//    private final double AUTO_RESET_DELAY  = 5.0;  //seconds to wait after leaving pits to change any pit commands because we have to wait on iRacing to do it first.
    private final double SIM_COMMANDS_DELAY= Server.getArg("pit-service-delay",0.5);  //seconds to delay after sending commands to before sending another command

    protected String m_trackType           = "";
    protected int    m_ME                  = -1;
    protected Data   m_trackSpeedLimit     = new Data("TrackPitSpeedLimit",0.0,"km/h");
    protected Data   m_trackLength         = new Data("TrackLength",0.0,"km");
    protected double m_sessionTime         = 0.0;
    protected double m_sessionStartTime    = 0.0;
    protected double m_sessionEndTime      = 0.0;
    protected String m_sessionType         = "";
    protected int    m_sessionVersion      = -1;
    protected boolean m_initialReset       = false;
    protected double m_resetTime           = 0.0; 
    protected boolean m_isNewCar           = false;
    protected boolean m_enableSIMSetupCommands = false;
    protected double m_sentSetupCommandsTimestamp = 0.0;
//    protected boolean m_forceSetupCommands  = false;
    protected boolean m_stoppedInPitStall   = false;
    
//    protected int   m_app_ini_autoResetPitBox=1;    //iRacing defaults to 1
    private iRacingSIMPlugin m_iRacingSIMPlugin;
    private Integer m_driversIdx            = -1;   //The index of the car in the DriverInfo.Drivers[] array.
    private String  m_number                = "";
    private Integer m_numberRaw             = -1;
    private String  m_url                   = "";

    private State   m_prevStatus            = new State(Car.Status.INVALID,0.0);
    private State   m_surfacelocation       = new State("",0.0);
    private int     m_discontinuality       = -1;  //start out -1, so the first time we enter the track it's not counted.
    private double  m_fuelLevel             = -1.0;
//    private double  m_fuelLevelPercent      = 0.0;
    private double  m_fuelAtStartFinish     = -1.0;
    private ArrayList<Boolean> m_invalidLaps= new ArrayList<Boolean>();   //indexed by lap completed, zero based (e.g. Lap 1 = index 0)
    private int     m_lapCompleted          = 0;   //cache the last lap completed so if they leave or blank out I'll have the last one.
    private double  m_lapCompletedPercent   = -1.0;
    private int     m_lapPitted             = 1;
    private ArrayList<Double> m_pitTimes = new ArrayList<Double>();
    private int     m_cautions              = 0;
    private int     m_cautionLaps           = 0;
    private int     m_lastCautionLap        = 0;
    private int     m_sessionFlags          = 0;
    private double  m_timeBeforeNextStateChange = 0.0;
    private ArrayList<Double> m_timeAtStartFinish = new ArrayList<Double>();
    private double  m_timeRemainingAtStartFinish = 0.0;
    private ArrayList<Double> m_fuelConsummed = new ArrayList<Double>();    //indexed by lap completed, zero based (e.g. Lap 1 = index 0)
    private double  m_repairTime            = 0.0;
    private double  m_repairTimeOptional    = 0.0;
    private double  m_pitLocation           = -1.0;
    private double  m_mergePointReference   = 0.0;
    private double  m_mergePoint            = 0.0;
    private int     m_lastKnownRadio        = 0;
    private int     m_lastKnownFrequency    = 0;    //Keep track of the last frequency they transmitted on.
    private Map<Integer,Integer> m_runningAverageSincePittingLaps = new HashMap<Integer,Integer>();
    private Map<Integer,Integer> m_averageSincePittingLaps = new HashMap<Integer,Integer>();
    private Map<Integer,Double> m_runningAverageSincePittingTime = new HashMap<Integer,Double>();
    private Map<Integer,Double> m_averageSincePittingTime = new HashMap<Integer,Double>();
    
    private ArrayList<Integer> m_myIncidentsLap     = new ArrayList<Integer>(); //indexed by current lap, zero based (e.g. Lap 1 = index 0)
    private int m_myIncidents                       = 0;
    private ArrayList<Integer> m_driverIncidentsLap = new ArrayList<Integer>(); //indexed by current lap, zero based (e.g. Lap 1 = index 0)
    private int m_driverIncidents                   = 0;
    private ArrayList<Integer> m_teamIncidentsLap   = new ArrayList<Integer>(); //indexed by current lap, zero based (e.g. Lap 1 = index 0)
    private int m_teamIncidents                     = 0;

//    private VarDataDouble m_fuelReader;
//    private VarDataDoubleSpeed m_speedReader;
    
    protected _Results m_results = new _Results();
    protected _ResultsQualifying m_resultsQualifying = new _ResultsQualifying();
    
//=========================  
//temporary variables for SessionDataCars class to store data
    static public class dynamicIRating {
        public double m_iRatingExp = 0.0;
        public double m_fudgeFactor = 0.0;
        public double m_change = 0.0;
//        public double m_points = 0.0;
        public double m_newIRating = 0;
        public double m_iRating = 0;
        public double m_expectedScore = 0.0;
        public double m_changeStarters = 0.0;
        public double m_expectedScoreNonStarter = 0.0;
        public double m_changeNonStarters = 0.0;
    }
    
    public dynamicIRating m_dynamicIRating = new dynamicIRating();
//=========================  

    public iRacingCar(iRacingSIMPlugin SIMPlugin) {
        super(SIMPlugin);
        m_iRacingSIMPlugin = SIMPlugin;
        _initialize();
    }

    public iRacingCar(iRacingSIMPlugin SIMPlugin, Integer id, String name, Integer driversIdx) {
        super(SIMPlugin, id, name, "com/SIMRacingApps/SIMPlugins/iRacing/Cars/"+Server.getArg(name.replaceAll("[ ]", "_"),name.replaceAll("[ ]", "_"))+".json");
        m_iRacingSIMPlugin = SIMPlugin;
        m_driversIdx = driversIdx;
        _initialize();
    }

    //session vars, updated only when there's a change to the session variables
    private class _ResultsQualifying {
        private int     m_sessionVersion = -1;
        private int     m_position      = 0;      public int getPosition()       { _refresh(); return m_position; }
        private int     m_positionClass = 0;      public int getPositionClass()  { _refresh(); return m_positionClass; }
        private int     m_lapBest       = 0;      public int getLapBest()        { _refresh(); return m_lapBest; }
        private double  m_lapTimeBest   = 0.0;    public double getLapTimeBest() { _refresh(); return m_lapTimeBest; }
        private int     m_incidents     = 0;      public int getIncidents()      { _refresh(); return m_incidents; }
        
        private _ResultsQualifying() {}

        private void _refresh() {
            //TODO: Does qualifying results ever change during a session? If not, only update them once?
            if ((!m_iRacingSIMPlugin.getSession().getIsReplay().getBoolean() && m_sessionVersion == m_iRacingSIMPlugin.getIODriver().getHeader().getSessionInfoUpdate()) || m_id == -1)
                return;

            m_sessionVersion = m_iRacingSIMPlugin.getIODriver().getHeader().getSessionInfoUpdate();

            try {
                for (int i = 0;;i++) {
                    String sCarIdx = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"CarIdx");
                    if (sCarIdx.isEmpty()) {
                        break;
                    }
                    else
                    if (Integer.parseInt(sCarIdx) == m_id) {
                        String s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"FastestTime");
                        if (!s.isEmpty() && Double.parseDouble(s) > 0.0) {
                            m_lapTimeBest = Double.parseDouble(s);
                            s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"FastestLap");
                            if (!s.isEmpty())
                                m_lapBest     = Integer.parseInt(s);
                        }
                        s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"Position");
                        if (!s.isEmpty() && Integer.parseInt(s) >= 0)
                            m_position    = Integer.parseInt(s) + 1;  //Qual positions are zero based

                        s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"ClassPosition");
                        if (!s.isEmpty() && Integer.parseInt(s) >= 0) {
                            if (m_iRacingSIMPlugin.getIODriver().build_december_9_2014())
                                m_positionClass    = Integer.parseInt(s) + 1;
                            else
                            if (m_iRacingSIMPlugin.getIODriver().build_november_12_2014())
                                m_positionClass    = Integer.parseInt(s);
                            else
                                m_positionClass    = Integer.parseInt(s) + 1;
                        }
                        else
                            m_positionClass    = m_position;
                        
                        s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"Incidents");
                        if (!s.isEmpty() && Integer.parseInt(s) >= 0) {
                            m_incidents = Integer.parseInt(s);
                        }
                        
                        break;
                    }
                }
            } 
            catch (IndexOutOfBoundsException e) {}
            catch (NumberFormatException e) {}
        }
    }

    private class _Results {
        private int     m_sessionVersion = -1;
        //private int     m_resultsCount          = 0;    public int getResultsCount()   { _refresh(); return m_resultsCount; }
        private int     m_position              = 0;    public int getPosition()       { _refresh(); return m_position; }
        private int     m_positionClass         = 0;    public int getPositionClass()  { _refresh(); return m_positionClass; }
        private double  m_lapTimeLast           = 0.0;  public double getLapTimeLast() { _refresh(); return m_lapTimeLast; }
        private double  m_lapTimeBest           = 0.0;  public double getLapTimeBest() { _refresh(); return m_lapTimeBest; }
        private int     m_lapBest               = 0;    public int getLapBest()        { _refresh(); return m_lapBest; }
        private int     m_lapCompleted          = 0;    public int getLapCompleted()   { _refresh(); return m_lapCompleted; }
        private int     m_lapsLed               = 0;    public int getLapsLed()        { _refresh(); return m_lapsLed; }
        private int     m_highestPosition       = 999;  public int getPositionHighest(){ _refresh(); return m_highestPosition == 999 ? 0 : m_highestPosition; }
        private int     m_highestPositionClass  = 999;  public int getPositionHighestClass(){ _refresh(); return m_highestPositionClass == 999 ? 0 : m_highestPositionClass; }
        private int     m_lowestPosition        = 0;    public int getPositionLowest(){ _refresh(); return m_lowestPosition; }
        private int     m_lowestPositionClass   = 0;    public int getPositionLowestClass(){ _refresh(); return m_lowestPositionClass; }
        
        private String  m_sessionNum                 = "";
        private ArrayList<Double>  m_lapTimes        = new ArrayList<Double>();  public ArrayList<Double>  getLapTimes()       { _refresh(); return m_lapTimes; } //indexed by lap completed, zero based (e.g. Lap 1 = index 0)
        private ArrayList<Integer> m_positions       = new ArrayList<Integer>(); public ArrayList<Integer> getPositions()      { _refresh(); return m_positions; } //indexed by lap completed, zero based (e.g. Lap 1 = index 0)
        private ArrayList<Integer> m_positionsClass  = new ArrayList<Integer>(); public ArrayList<Integer> getPositionsClass() { _refresh(); return m_positionsClass; } //indexed by lap completed, zero based (e.g. Lap 1 = index 0)

        private Map<String,Double>  m_lapTimeBestDriver       = new HashMap<String,Double>();
        public double getLapTimeBestDriver(String driverName) { 
            _refresh();
            Double lapTime = m_lapTimeBestDriver.get(driverName);
            return lapTime == null ? 0.0 : lapTime.doubleValue(); 
        }
        private Map<String,Integer> m_lapBestDriver           = new HashMap<String,Integer>();
        public double getLapBestDriver(String driverName) { 
            _refresh();
            Integer lap = m_lapBestDriver.get(driverName);
            return lap == null ? 0 : lap.intValue(); 
        }
        private Map<String,Double>  m_lapTimeBestCleanDriver  = new HashMap<String,Double>();
        public double getLapTimeBestCleanDriver(String driverName) { 
            _refresh();
            Double lapTime = m_lapTimeBestCleanDriver.get(driverName);
            return lapTime == null ? 0.0 : lapTime.doubleValue(); 
        }
        
        public _Results() {}
        private void _refresh() {
            int m_position_2015 = -1;
            int m_positionClass_2015 = -1;
            int m_lapCompleted_2015 = -1;

//These were causing funny results. Don't need them, unless replay, since it works from the session string without them.            
            boolean replayFromFile = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","SimMode").equals("replay");

//if (m_number.equals("07"))
//    m_id = m_id;

            if (m_iRacingSIMPlugin.getIODriver().build_June_9_2015() 
            &&  m_id >= 0
            &&  m_iRacingSIMPlugin.getSession().getIsReplay().getBoolean()  //only if in a replay
            &&  replayFromFile
            ) {
                //according to a post in the forums, this is updated during a replay while the session info isn't.
//                "CarIdxClassPosition": [],
//                "CarIdxEstTime": [],
//                "CarIdxF2Time": [],
//                "CarIdxGear": [],
//                "CarIdxLap": [],
//                "CarIdxLapCompleted": [],
//                "CarIdxLapDistPct": [],
//                "CarIdxOnPitRoad": [],
//                "CarIdxPosition": [],
//                "CarIdxRPM": [],
//                "CarIdxSteer": [],
//                "CarIdxTrackSurface": [],
//                "CarIdxTrackSurfaceMaterial": [],
                
                m_position_2015      = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("CarIdxPosition",m_id);
                m_positionClass_2015 = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("CarIdxClassPosition",m_id);
                m_lapCompleted_2015 = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("CarIdxLapCompleted",m_id);
            }
            
            if (m_id == -1 || (m_sessionVersion == m_iRacingSIMPlugin.getIODriver().getHeader().getSessionInfoUpdate() && !m_iRacingSIMPlugin.getSession().getIsReplay().getBoolean()))
                return;

            m_sessionVersion = m_iRacingSIMPlugin.getIODriver().getHeader().getSessionInfoUpdate();

            //our position in the results array could change every update, so we have to scan the array for this car every time
            int index = -1;
            int resultsCount = 0;
            int position = 0;
            int positionClass = 0;
            String sessionNum = m_iRacingSIMPlugin.getIODriver().getVars().getString("SessionNum");

            try {
                for (int i = 0;;i++) {
                    String sCarIdx = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(i),"CarIdx");

                    if (sCarIdx.isEmpty()) {
                        break;
                    }
                    resultsCount++;
                    if (Integer.parseInt(sCarIdx) == m_id) {
                        index = i;
                        break;
                    }
                }
            } catch (IndexOutOfBoundsException e) {}

            //m_resultsCount = resultsCount;

            try {
                //String sessionType = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(m_iRacingSIMPlugin.getIODriver().getVars().getInteger("SessionNum")),"SessionType").toUpperCase();

                if (resultsCount > 0) {
                    
                    //if this is a new session, then zero out the arrays so data will not carry over
                    if (!m_sessionNum.equals(sessionNum)) {
                        m_sessionNum = sessionNum;
                        
                        m_position        = 0;
                        m_positionClass   = 0;
                        m_lapTimeLast     = 0.0;
                        m_lapTimeBest     = 0.0;
                        m_lapBest         = 0;
                        m_lapCompleted    = 0;
                        m_lapsLed         = 0;
                        m_lapTimes        = new ArrayList<Double>();
                        m_positions       = new ArrayList<Integer>();
                        m_positionsClass  = new ArrayList<Integer>();
                    }                  
                    
                    
                    if (index == -1) { //if we haven't posted a lap yet, zero out the position so qualifying doesn't bleed into the race.
                        position    = 0;
                        positionClass = 0;
                        m_lapTimeBest = 0.0;
                        m_lapBest     = 0;
                        m_lapsLed     = 0;
                    }
                    else {
                        String s;
                        if (replayFromFile && m_position_2015 >= 0) {  //if new value is valid use it
                            position = m_position_2015;
                        }
                        else {
                            s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(index),"Position");
                            if (!s.isEmpty() && Integer.parseInt(s) > 0)
                                position      = Integer.parseInt(s);
                        }
                        
                        if (replayFromFile && m_positionClass_2015 >= 0 ) {  //if new value is valid use it
                            positionClass = m_positionClass_2015;
                        }
                        else {
                            s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(index),"ClassPosition");
                            if (!s.isEmpty() && Integer.parseInt(s) >= 0) {
                                if (m_iRacingSIMPlugin.getIODriver().build_december_9_2014())
                                    positionClass = Integer.parseInt(s) + 1;
                                else
                                if (m_iRacingSIMPlugin.getIODriver().build_november_12_2014())
                                    positionClass = Integer.parseInt(s);
                                else
                                    positionClass = Integer.parseInt(s) + 1;
                            }
                            else
                                positionClass = m_position;
                        }

                        if (replayFromFile && m_lapCompleted_2015 >= 0)
                        {
                            m_lapCompleted = m_lapCompleted_2015;
                        }
                        else {
                            s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(index),"LapsComplete");
                            if (!s.isEmpty() && Integer.parseInt(s) > 0.0) {
                                //do this for when we've just entered the session and we don't know the laps completed.
                                if (m_lapCompleted < Integer.parseInt(s)) {
                                    
                                    m_lapCompleted = Integer.parseInt(s);
    
                                }
                            }
                        }
                        
                        //when in a replay and you exit the car, the positions in the var
                        //will zero out. When that happens, I will keep the previous values.
                        if (position > 0) {
                            m_position = position;
                            m_positionClass = positionClass;
                            
                            //now save this last known position for this lap has history
                            while (m_positions.size() < m_lapCompleted) {
                                m_positions.add(m_position);
                            }
                            while (m_positionsClass.size() < m_lapCompleted) {
                                m_positionsClass.add(m_positionClass);
                            }
                        }

                        s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(index),"LapsLed");
                        if (!s.isEmpty() && Integer.parseInt(s) > 0.0) {
                            //do this for when we've just entered the session and we don't know the laps completed.
                            if (m_lapsLed < Integer.parseInt(s))
                                m_lapsLed = Integer.parseInt(s);
                        }
                        
                        Double d = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("LapLastLapTime");
                        if (!isME() || d <= 0.0)
                            d = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getDouble("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(index),"LastTime");
                        if (d > 0.0 /* && Double.parseDouble(s) > 0.0 *can get zero if meatball is out*/) {
                            m_lapTimeLast = d;
                            
                            //if not recording laps, make it obvious
//                            if (m_lapTimeLast <= 0.0)
//                                m_lapTimeLast = -1;
                            
                            //if we're missing laps, build up the array with the current one.
                            while (m_lapTimes.size() < m_lapCompleted) {
                                m_lapTimes.add(0.0);
                            }
                            if (m_lapCompleted > 0)
                                m_lapTimes.set(m_lapCompleted - 1,m_lapTimeLast);    //keep updating the last lap in case iRacing changes it at a different tick than the lap counter
                        }

                        d = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getDouble("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(index),"FastestTime");
                        if (d > 0.0) {
                            m_lapTimeBest   = d;
                            s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",sessionNum,"ResultsPositions",Integer.toString(index),"FastestLap");
                            if (!s.isEmpty())
                                m_lapBest       = Integer.parseInt(s);
                        }
                        
                        //track best lap times per driver in a team session
                        Double lapTime = m_lapTimeBestDriver.get(getDriverName(false).getString());
                        if (m_lapTimeLast > 0.0 && lapTime == null) {
                            m_lapTimeBestDriver.put(getDriverName(false).getString(), m_lapTimeLast);
                            m_lapBestDriver.put(getDriverName(false).getString(), m_lapCompleted);
                        }
                        else
                        if (m_lapTimeLast > 0.0 && (lapTime == null || m_lapTimeLast < lapTime)) {
                            m_lapTimeBestDriver.replace(getDriverName(false).getString(), m_lapTimeLast);
                            m_lapBestDriver.replace(getDriverName(false).getString(), m_lapCompleted);
                        }

                        lapTime = m_lapTimeBestCleanDriver.get(getDriverName(false).getString());
                        if (m_lapTimeLast > 0.0 && lapTime == null && m_driverIncidentsLap.get(m_lapCompleted-1) == 0) {
                            m_lapTimeBestCleanDriver.put(getDriverName(false).getString(), m_lapTimeLast);
                        }
                        else
                        if (m_lapTimeLast > 0.0 && (lapTime == null || m_lapTimeLast < lapTime) && m_lapCompleted > 0 && m_lapCompleted <= m_driverIncidentsLap.size() && m_driverIncidentsLap.get(m_lapCompleted-1) == 0) {
                            m_lapTimeBestCleanDriver.replace(getDriverName(false).getString(), m_lapTimeLast);
                        }

                    }
                }
                else {
//                    if (sessionType.equalsIgnoreCase("RACE")) { //use qualifying results if session is RACE
                        m_position      = m_resultsQualifying.getPosition();
                        m_positionClass = m_resultsQualifying.getPositionClass();
                        m_lapTimeBest   = m_resultsQualifying.getLapTimeBest();
                        m_lapBest       = m_resultsQualifying.getLapBest();
//                    }
                }
                
                if (m_position > 0) {
                    m_highestPosition = Math.min(m_highestPosition, m_position);
                    m_lowestPosition  = Math.max(m_lowestPosition, m_position);
                }
                if (m_positionClass > 0) {
                    m_highestPositionClass = Math.min(m_highestPositionClass, m_positionClass);
                    m_lowestPositionClass  = Math.max(m_lowestPositionClass, m_positionClass);
                }
                
            }  
            catch (IndexOutOfBoundsException e) {}
            catch (NumberFormatException e) {}
        }
    }

    private boolean _sendSetupCommands() {

        //only support sending pit commands for ME
        if (!this.isME() 
        || !m_enableSIMSetupCommands
        || m_surfacelocation.getState().equals(TrackSurface.NotInWorld)
        || !m_iRacingSIMPlugin.getIODriver().getVars().getBoolean("IsOnTrack")
        || (m_sentSetupCommandsTimestamp + SIM_COMMANDS_DELAY > m_sessionTime) //give results time to come back before sending more
        //TODO: test pit commands if you're a Crew Chief for another driver.
        )
            return false;

        //This gets called every tick to send the values to the SIM
        iRacingGauge fuellevel = (iRacingGauge) _getGauge(Gauge.Type.FUELLEVEL);
        iRacingGauge tearoff   = (iRacingGauge) _getGauge(Gauge.Type.WINDSHIELDTEAROFF);
        iRacingGauge LF        = (iRacingGauge) _getGauge(Gauge.Type.TIREPRESSURELF);
        iRacingGauge LR        = (iRacingGauge) _getGauge(Gauge.Type.TIREPRESSURELR);
        iRacingGauge RF        = (iRacingGauge) _getGauge(Gauge.Type.TIREPRESSURERF);
        iRacingGauge RR        = (iRacingGauge) _getGauge(Gauge.Type.TIREPRESSURERR);
        iRacingGauge fastRepair= (iRacingGauge) _getGauge(Gauge.Type.FASTREPAIRS);
        iRacingGauge compound  = (iRacingGauge) _getGauge(Gauge.Type.TIRECOMPOUND);

        //get the latest timestamp of the supported gauges
        double timestamp = Math.abs(LF._getSIMCommandTimestamp());
        timestamp = Math.max(timestamp, Math.abs(LR._getSIMCommandTimestamp()));
        timestamp = Math.max(timestamp, Math.abs(RF._getSIMCommandTimestamp()));
        timestamp = Math.max(timestamp, Math.abs(RR._getSIMCommandTimestamp()));
        timestamp = Math.max(timestamp, Math.abs(fuellevel._getSIMCommandTimestamp()));
        timestamp = Math.max(timestamp, Math.abs(tearoff._getSIMCommandTimestamp()));
        timestamp = Math.max(timestamp, Math.abs(fastRepair._getSIMCommandTimestamp()));
        timestamp = Math.max(timestamp, Math.abs(compound._getSIMCommandTimestamp()));
        
        //test these as a group because if any are sent, we start with a #clear, otherwise nothing is sent
        //give time for all the commands to come in before sending them
        if (timestamp != 0.0 
        && (timestamp + SIM_COMMANDS_DELAY <= m_sessionTime)
        ) {
            boolean removeTearoff = tearoff._getSIMCommandTimestamp() > 0.0;
            
            BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_Clear);
            Server.logger().info(String.format("_sendSetupCommands() Clear"));

            //NOTE: For the electric cars where they use kWh, this still works because there's no conversion to liter.
            if (fuellevel._getSIMCommandTimestamp() > 0.0 && fuellevel._getSIMCommandValue() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/l",fuellevel.getType().getString(),fuellevel._getSIMCommandValue())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_Fuel, fuellevel._getSIMCommandValue());
                removeTearoff = true;
            }
            else //keep existing setting
            if (fuellevel._getSIMCommandTimestamp() == 0.0 && fuellevel.getChangeFlag().getBoolean() && fuellevel.getValueNext().convertUOM("l").getInteger() > 0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/l",fuellevel.getType().getString(),fuellevel.getValueNext().convertUOM("l").getInteger())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_Fuel, fuellevel.getValueNext().convertUOM("l").getInteger());
                removeTearoff = true;
            }
            
            if (compound._getSIMCommandTimestamp() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d",compound.getType().getString(),compound._getSIMCommandValue())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_TC, compound._getSIMCommandValue());
                removeTearoff = true;
            }
            else //keep existing setting
            if (compound._getSIMCommandTimestamp() == 0.0 && compound.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d",compound.getType().getString(),compound.getValueNext().getInteger())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_TC, compound.getValueNext().getInteger());
                removeTearoff = true;
            }

            //as of Sept. 2017 build, iRacing makes you change both tires on the same side at the same time
            //found it will the both on, when only one is set, but it will not turn both off
            //This just helps inforce that.
            if (RF._getSIMCommandTimestamp() != 0.0 && RR._getSIMCommandTimestamp() == 0.0)
                RR._setSIMCommandTimestamp(RF._getSIMCommandTimestamp() > 0.0 ? true : false, RR.getValueNext().convertUOM("kPa").getDouble());
            if (RR._getSIMCommandTimestamp() != 0.0 && RF._getSIMCommandTimestamp() == 0.0)
                RF._setSIMCommandTimestamp(RR._getSIMCommandTimestamp() > 0.0 ? true : false, RF.getValueNext().convertUOM("kPa").getDouble());
            if (LF._getSIMCommandTimestamp() != 0.0 && LR._getSIMCommandTimestamp() == 0.0)
                LR._setSIMCommandTimestamp(LF._getSIMCommandTimestamp() > 0.0 ? true : false, LR.getValueNext().convertUOM("kPa").getDouble());
            if (LR._getSIMCommandTimestamp() != 0.0 && LF._getSIMCommandTimestamp() == 0.0)
                LF._setSIMCommandTimestamp(LR._getSIMCommandTimestamp() > 0.0 ? true : false, LF.getValueNext().convertUOM("kPa").getDouble());
            
            if (RF._getSIMCommandTimestamp() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",RF.getType().getString(),RF._getSIMCommandValue())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_RF, RF._getSIMCommandValue());
                removeTearoff = true;
            }
            else //keep existing setting
            if (RF._getSIMCommandTimestamp() == 0.0 && RF.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",RF.getType().getString(),RF.getValueNext().convertUOM("kpa").getInteger())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_RF, RF.getValueNext().convertUOM("kpa").getInteger());
                removeTearoff = true;
            }
            
            if (RR._getSIMCommandTimestamp() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",RR.getType().getString(),RR._getSIMCommandValue())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_RR, RR._getSIMCommandValue());
                removeTearoff = true;
            }
            else //keep existing setting
            if (RR._getSIMCommandTimestamp() == 0.0 && RR.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",RR.getType().getString(),RR.getValueNext().convertUOM("kpa").getInteger())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_RR, RR.getValueNext().convertUOM("kpa").getInteger());
                removeTearoff = true;
            }

            if (LF._getSIMCommandTimestamp() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",LF.getType().getString(),LF._getSIMCommandValue())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_LF, LF._getSIMCommandValue());
                removeTearoff = true;
            }
            else //keep existing setting
            if (LF._getSIMCommandTimestamp() == 0.0 && LF.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",LF.getType().getString(),LF.getValueNext().convertUOM("kpa").getInteger())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_LF, LF.getValueNext().convertUOM("kpa").getInteger());
                removeTearoff = true;
            }

            if (LR._getSIMCommandTimestamp() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",LR.getType().getString(),LR._getSIMCommandValue())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_LR, LR._getSIMCommandValue());
                removeTearoff = true;
            }
            else //keep existing setting
            if (LR._getSIMCommandTimestamp() == 0.0 && LR.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%d/kPa",LR.getType().getString(),LR.getValueNext().convertUOM("kpa").getInteger())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_LR, LR.getValueNext().convertUOM("kpa").getInteger());
                removeTearoff = true;
            }

            if (fastRepair._getSIMCommandTimestamp() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setChangeFlag/Y",fastRepair.getType().getString())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(),BroadcastMsg.PitCommandMode.PitCommand_FR);
                removeTearoff = true;
            }
            else //keep existing setting
            if (fastRepair._getSIMCommandTimestamp() == 0.0 && fastRepair.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setChangeFlag/Y",fastRepair.getType().getString())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_FR);
                removeTearoff = true;
            }
            
            if (removeTearoff) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setChangeFlag/Y",tearoff.getType().getString())));
                BroadcastMsg.PitCommandMode.send(m_iRacingSIMPlugin.getIODriver(),BroadcastMsg.PitCommandMode.PitCommand_WS);
            }

//            m_forceSetupCommands = false;
            LF._clearSIMCommandTimestamp();
            LR._clearSIMCommandTimestamp();
            RF._clearSIMCommandTimestamp();
            RR._clearSIMCommandTimestamp();
            fuellevel._clearSIMCommandTimestamp();
            tearoff._clearSIMCommandTimestamp();
            fastRepair._clearSIMCommandTimestamp();
            compound._clearSIMCommandTimestamp();
            
            m_sentSetupCommandsTimestamp = m_sessionTime; 
            return true;
        }
        return false;
    }

    public String _getDriversIdx() {
        return m_driversIdx.toString();
    }
    
    private static FindFile m_clubnames = null;
    
    @SuppressWarnings("unchecked")
    private String _getClubNumber() {
        String clubID = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"ClubID");
        
        if (!clubID.isEmpty())
            return clubID;
        
        String club = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"ClubName");
        
//I go this list from the Club drop down at http://members.iracing.com/membersite/member/statsseries.jsp
        if (club.isEmpty())
            return "0";
        
        if (m_clubnames == null) {
            try {
                m_clubnames = new FindFile("com/SIMRacingApps/SIMPlugins/iRacing/ClubNames.json");
            } catch (FileNotFoundException e) {
                Server.logStackTrace(e);
            }
        }
        
        if (m_clubnames != null) {
            Map<String,Long> map;
            
            if (club.startsWith("Hispanoam")) //TODO: can't put extended characters in JSON file? try unicode
                map = (Map<String,Long>)m_clubnames.getJSON().get("Hispanoam");
            else
                map = (Map<String,Long>)m_clubnames.getJSON().get(club);
            
            if (map != null) {
                return map.get("id").toString();
            }
        }
        
        Server.logger().fine("Unknown Club Found, "+club);
        
        return "0";
    }
    
    @Override
    public Data getBearing(String UOM) {
    	Data d = super.getBearing(UOM);
    	if (isValid() && Server.getArg("bearing-uses-actual-yaw", true)) {
    		double yawNorth = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("YawNorth");
    		if (yawNorth != -1) {
    			d.setValue(yawNorth,"RAD");
    			double bearing_deg = d.convertUOM("DEG").getDouble();
    			bearing_deg += 270;
    			if (bearing_deg >= 360.0) 
    				bearing_deg -= 360;
    			d.setValue(bearing_deg,"DEG");
    		}
    	}
    	return d.convertUOM(UOM);
    }
    
    @Override
    public Data getCautions() {
        Data d = super.getCautions();
        d.setValue(m_cautions,"int",Data.State.NORMAL);
        return d;
    }
    
    @Override
    /**
     * {@inheritDoc}
     * This is a test of document inheritance.
     */
    public Data getClassColor() {
        Data d = super.getClassColor();
        d.setState(Data.State.OFF);

        if (isValid()) {
            String color = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassColor");
            d.setValue(color.isEmpty() ? 0 : Integer.parseInt(color));

//for testing only
/**            
if (m_id > 12)            
    d.setValue(0xFF0000);
else
if (m_id > 6)            
    d.setValue(0x00FF00);
else
    d.setValue(0x0000FF);
/**/    
        }
        return d;
    }
    
    @Override
    /**
     * {@inheritDoc}
     * This is a test of document inheritance.
     */
    public Data getClassName() {
        Data d = super.getClassName();
        d.setState(Data.State.OFF);

        if (isValid()) {
            String name = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassShortName");
            d.setValue(name == null ? "" : name,"String",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    /**
     * {@inheritDoc}
     * This is a test of document inheritance.
     */
    public Data getColor() {
        Data d = super.getColor();
        d.setState(Data.State.OFF);

        // CarDesignStr: 0,000000,55040d,ffffff,ed2129
        // the last value is optional and sometimes missing. It is the tire rim color.
        // the color of the car is the 2nd number.

        if (isValid() && !isPaceCar() && Server.getArg("use-sim-colors", true)) {
            try {
                String design = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarDesignStr");
                String s[]    = design.split("[,;.-]");
                if (s.length > 1)
                    d.setValue(Integer.decode("0x"+s[1]),"RGB",Data.State.NORMAL);
            } catch (NumberFormatException e) {}
        }
        return d;
    }

    @Override
    public Data getColorNumber() {
        Data d = super.getColorNumber();
        d.setState(Data.State.OFF);

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The color of the number is the 3rd number.
        if (isValid() && !isPaceCar() && Server.getArg("use-sim-colors", true)) {
            try {
                String design = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
                String s[]    = design.split("[,;.-]");
                if (s.length > 2) {
                    int color = Integer.decode("0x"+s[2]);
    //                //if the Car Color and the Number Color is the same, invert it so we can see it
    //                if (color == getColor().getInteger())
    //                    color = getColor().getInteger() ^ 0xffffff;
                    d.setValue(color,"RGB",Data.State.NORMAL);
                }
            } catch (NumberFormatException e) {}
        }
        return d;
    }

    @Override
    public Data getColorNumberBackground() {
        Data d = super.getColorNumberBackground();
        d.setState(Data.State.OFF);

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The color of the number's background is the 5th number.
        if (isValid() && !isPaceCar() && Server.getArg("use-sim-colors", true)) {
            try {
                String design = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
                String s[]    = design.split("[,;.-]");
                if (s.length > 4) {
                    d.setValue(Integer.decode("0x"+s[4]),"RGB",Data.State.NORMAL);
                }
                //if the Car Color background is the same as car's background invert the numbers background
                String cardesign = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarDesignStr");
                String s2[]    = cardesign.split("[,;.-]");
                if (s2.length > 3) {
                    try {
                        if (d.getInteger() == Integer.decode("0x"+s2[3]))
                            d.setValue(d.getInteger() ^ 0xffffff);
                    } catch (NumberFormatException e) {}
                }
            } catch (NumberFormatException e) {}
        }
        return d;
    }

    @Override
    public Data getColorNumberOutline() {
        Data d = super.getColorNumberOutline();
        d.setState(Data.State.OFF);

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The color of the number's outline is the 4th number.
        if (isValid() && !isPaceCar() && Server.getArg("use-sim-colors", true)) {
            try {
                String design = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
                String s[]    = design.split("[,;.-]");
                if (s.length > 3) {
                    d.setValue(Integer.decode("0x"+s[3]),"RGB",Data.State.NORMAL);
                }
            } catch (NumberFormatException e) {}
        }
        return d;
    }

    @Override
    public Data getDescription() {
        Data d = super.getDescription();
        d.setState(Data.State.OFF);

        if (isValid()) {
            String desc = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarScreenName");
            if (!desc.isEmpty()) {
                d.setValue(desc,"",Data.State.NORMAL);
            }
        }
        return d;
    }
    
    @Override
    public Data getDiscontinuality() {
        Data d = super.getDiscontinuality();
        d.setValue(m_discontinuality < 0 ? 0 : m_discontinuality);
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getDriverClubName() {
        Data d = super.getDriverClubName();
        d.setState(Data.State.OFF);

        if (isValid()) {
            d.setValue( m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"ClubName"),"",Data.State.NORMAL );
        }
        return d;
    }

    @Override
    public Data getDriverDivisionName() {
        Data d = super.getDriverDivisionName();
        d.setState(Data.State.OFF);

        if (isValid()) {
            d.setValue( m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"DivisionName"),"",Data.State.NORMAL );
        }
        return d;
    }

    @Override
    public Data getDriverInitials() {
        Data d = super.getDriverInitials();
        d.setState(Data.State.OFF);

        if (isValid()) {
            d.setValue( m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"Initials") );
            if (d.getString().isEmpty() && getIsPaceCar().getBoolean())
                d.setValue("PC");
            else
            if (d.getString().isEmpty() && m_name.equals("PITSTALL"))
                d.setValue("PIT");
            else
            if (d.getString().isEmpty()) {
                String s[] = getDriverNameShort(false).getString().split(" ");
                if (s.length == 1 && !s[0].isEmpty())
                    d.setValue(s[0].substring(0, 1));
                else
                if (s.length > 1 && !s[s.length-1].isEmpty() && !s[0].isEmpty()) {
                    d.setValue(s[s.length-1].substring(0, 1) + s[0].substring(0, 1));
                }
            }
        }
        return d;
    }

    @Override
    public Data getDriverLicenseColor() {
        Data d = super.getDriverLicenseColor();
        d.setState(Data.State.OFF);

        try {
            if (isValid()) {
                d.setValue(
                    Integer.parseUnsignedInt(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicColor"))
                  , "RGB"
                  , Data.State.NORMAL
                );
            }
        } catch (NumberFormatException e) {}
        
        return d;
    }

    @Override
    public Data getDriverLicenseColorText() {
        Data d = super.getDriverLicenseColorText();
        d.setState(Data.State.OFF);

        if (isValid()) {
            int license = getDriverLicenseColor().getInteger();
            /* determine a text color that will look good when the background color is the license color */
            if (license == 16706564)   /* yellow */
                d.setValue(0x000000);  /* black */
            else
                d.setValue(0xffffff);  /* white */
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getDriverName(boolean allowMapping) {
        Data d = super.getDriverName(allowMapping);
        d.setState(Data.State.OFF);
        if (isValid()) {
            String driverName = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserName");
            if (driverName.isEmpty() && m_name.equals("PITSTALL"))
                driverName = "Pit Stall";
            
            if (allowMapping)
                d.setValue(Server.getArg(driverName,driverName),"",Data.State.NORMAL);
            else
                d.setValue(driverName,"",Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getDriverNameShort(boolean allowMapping) {
        Data d = super.getDriverNameShort(allowMapping);
        d.setState(Data.State.OFF);
        if (isValid()) {
            String name = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"AbbrevName");
            if (name.isEmpty() && getIsPaceCar().getBoolean())
                name = "Pace Car";
            else
            if (name.isEmpty() && m_name.equals("PITSTALL"))
                name = "Pit";
            else
            if (name.isEmpty()) {
                //try and make the long name shorter
                String names[] = getDriverName(false).getString().split(" ");
                if (names.length > 0) {
                    name = names[names.length-1];   //get the last name
                    //if that returns a modifier, get the previous name
                    if (name.equalsIgnoreCase("JR")
                    ||  name.equalsIgnoreCase("JR.")
                    ||  name.equalsIgnoreCase("I")
                    ||  name.equalsIgnoreCase("I.")
                    ||  name.equalsIgnoreCase("II")
                    ||  name.equalsIgnoreCase("II.")
                    ||  name.equalsIgnoreCase("III")
                    ||  name.equalsIgnoreCase("III.")
                    ) {
                        if (names.length > 1)
                            name = names[names.length-2];
                    }
                    
                    //now tack on the first initial
                    if (!names[0].isEmpty())
                        name += ", " + names[0].substring(0, 1);
                }
            }
            
            if (allowMapping)
                d.setValue(Server.getArg(name,name),"",Data.State.NORMAL);
            else
                d.setValue(name,"",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getDriverRating() {
        Data d = super.getDriverRating();
        d.setState(Data.State.OFF);

        try {
            if (isValid()) {
                String iRating      = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"IRating");
                Integer LicLevel    = Integer.parseInt(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicLevel"));
                Integer LicSubLevel = Integer.parseInt(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicSubLevel"));
                //String division     = _Car(caridx).getCarDivisionName().getString();
                //String s[] = division.split(" ");
    
                String l = "?";
    //            int license  = getDriverLicenseColor().getInteger();
    //            if (license == 0x000000) l = "P";
    //            if (license == 0x0153db) l = "A";
    //            if (license == 0x00c702) l = "B";
    //            if (license == 0xfeec04) l = "C";
    //            if (license == 0xfc8a27) l = "D";
    //            if (license == 0xfc0706) l = "R";
    
                //d.setValue(iRating+"-"+l+(s.length > 1 ? s[1] : "?"));
    
                //iLevel 0-4   R
                //       5-8   D
                //       9-12  C
                //       13-16 B
                //       17-20 A
                //       21-24 P
                //       25-28 WC
    
                /* From David Tucker in the forums
                 * http://members.iracing.com/jforum/posts/list/1375/1470675.page#8498515
                 *
                    const char * licenceToString(int licLevel, int licSubLevel)
                    {
                        // static! not reentrant!
                        static char tstr[128];
    
                        const char *licLetStr[] = {"R", "R", "D", "C", "B", "A", "P", "WC"};
                        int maxLicStr = 8;
    
                        int licNum = licLevel >> 2; // licence / 4
                        if(licNum < 0 || licNum > maxLicStr)
                            licNum = 0;
    
                        float licVal = licSubLevel / 100.0f;
    
                        sprintf(tstr, "%s %04.2f", licLetStr[licNum], licVal);
    
                        return tstr;
                    }
    
                 */
    
                //with corrections posted by Mahail Latyshov
                int licNum = Math.max(0,LicLevel-1) / 4;
                if (licNum < 0 || licNum > 8)
                    licNum = 0;
                String[] licStr = {"R", "D", "C", "B", "A", "P", "WC"};
                l = licStr[licNum];
    
                if (this.m_dynamicIRating.m_newIRating > 0 
                && Server.getArg("dynamic-irating", true)
                && ((iRacingSession)this.m_SIMPlugin.getSession())._isOfficial()
                && (this.m_SIMPlugin.getSession().getNumberOfCarClasses().getInteger() <= 1 || Server.getArg("dynamic-irating-multiclass", false))
                )
                    d.setValue(String.format("%s(%+.0f)%s%.2f",iRating,this.m_dynamicIRating.m_change,l,LicSubLevel/100.0));
                else
                    d.setValue(String.format("%s-%s%.2f",iRating,l,LicSubLevel/100.0));
                d.setState(Data.State.NORMAL);
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public Data getDriverRatingDelta() {
        Data d = super.getDriverRatingDelta();
        d.setState(Data.State.OFF);

        try {
            if (isValid()) {
                if (this.m_dynamicIRating.m_newIRating > 0 
                && Server.getArg("dynamic-irating", true)
                && (this.m_SIMPlugin.getSession().getNumberOfCarClasses().getInteger() <= 1 || Server.getArg("dynamic-irating-multiclass", false))
                )
                    d.setValue(String.format("%+.0f",this.m_dynamicIRating.m_change));
                else
                    d.setValue("0");
                d.setState(Data.State.NORMAL);
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public Data getFuelLevelPerLap(int lapsToAverage, String UOM) {
        Data FuelPerLap  = super.getFuelLevelPerLap(lapsToAverage, UOM);

        if (isME()) {
            FuelPerLap.setState(Data.State.OFF);
            
            if (isValid()) {
                int c = 0;
                double totalfuel = 0.0;
                if (lapsToAverage > 0) {
                    //now take the average of the last "laps" laps that were not invalid laps
                    //if we don't have enough valid "laps", then use as many as you have.
                    for (int i=m_fuelConsummed.size()-1; c < lapsToAverage && i >= 0; i--) {
                        if (!m_invalidLaps.get(i)) {
                            totalfuel += m_fuelConsummed.get(i);
                            c++;
                        }
                    }
                }
                else {
                    //find the worse lap, that was not an invalid lap
                    for (int i=0; i < m_fuelConsummed.size(); i++) {
                        if (!m_invalidLaps.get(i)) {
                            if (totalfuel < m_fuelConsummed.get(i)) {
                                totalfuel = m_fuelConsummed.get(i);
                                c = 1;
                            }
                        }
                    }
                }
        
                if (m_iRacingSIMPlugin.getIODriver().isConnected()) {
                    if (c > 0) {
                        FuelPerLap.setValue(totalfuel/c,m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);
                    }
                    else {
                        FuelPerLap.setValue(0.0,m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);
                    }
                    FuelPerLap.setState(Data.State.NORMAL);
                }
                
                FuelPerLap.addConversion(this._getGauge(Gauge.Type.FUELLEVEL).getValueCurrent());
            }
        }

        return FuelPerLap.convertUOM(_getGauge(Gauge.Type.FUELLEVEL).getUOM().getString()).convertUOM(UOM);
    }

    @Override
    public Data getFuelLevelNeeded(int lapsToAverage,double laps, String UOM) {
        Data d = super.getFuelLevelNeeded(lapsToAverage,laps,UOM);
        if (isME()) {
            d.setState(Data.State.OFF);
            if (isValid()) {
                d.addConversion(this._getGauge(Gauge.Type.FUELLEVEL).getValueCurrent());
                d.setState(Data.State.NORMAL);
            }
        }
        return d.convertUOM(UOM);
    }
    
    @Override
    public Data getFuelLevelToFinish(int lapsToAverage,double laps, String UOM) {
        Data d = super.getFuelLevelToFinish(lapsToAverage,laps,UOM);
        if (isME()) {
            d.setState(Data.State.OFF);
            if (isValid()) {
                d.addConversion(this._getGauge(Gauge.Type.FUELLEVEL).getValueCurrent());
                d.setState(Data.State.NORMAL);
            }
        }
        return d.convertUOM(UOM);
    }
    
//    @Override
//    public    void    setGauge(String gaugeType, String var, double value,String uom) {
//        super.setGauge(gaugeType, var, value, uom);
//    }


    /**
     * Automatic Pit Commands will not be enabled until a client calls this function to find out.
     * The client should not call this, unless they want to control the SIM, because it enables them.
     */
    @Override
    public Data getHasAutomaticPitCommands() {
        Data d = super.getHasAutomaticPitCommands();
//if (m_id != -1 && m_id ==  m_iRacingSIMPlugin.getIODriver().getVars().getInteger("PlayerCarIdx"))
//    m_id = m_id * 1;
        if (isME()) {
            if (!m_enableSIMSetupCommands)
                Server.logger().info(String.format("%s.getHasAutomaticPitCommands() is returning Y",this.getClass().getName()));
            m_enableSIMSetupCommands = true;
            d.setValue(true);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getId() {
        Data d = super.getId();
        d.setState(Data.State.OFF);

        if (isValid()) {
            d.setValue(m_id);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    /*
     * https://members.iracing.com/jforum/posts/list/3794777.page#12413480
     * 
     * // support custom paints
     * pk_car.png - render the car
     * pk_body.png - render our new user avatar (suit, hed, helmet)
     * pk_suit.png - render just the suit without helmet or head
     * pk_helmet.png - render a helmet
     * 
     * // don't support custom paints
     * pk_head.png - pre-rendered view of the avatars face
     * pk_number.png - render a car's number stamp, can render any number.
     * pk_club.png - all club logos
     * pk_sponsor.png - all sponsor decals.
     * 
     * There are a series of optional arguments that control how the render works.  Each of these below can be passed for any pk_XXX.png entry, although not all make sense in all cases.  Most can be skipped, a reasonable default will be filled in for you.
     * 
     * // shared
     * size=d // size of the image to show
     * view=d // alternate rendered view of image
     * 
     * // stamp layers
     * licCol=x // hex color FFFFFF
     * club=d // club decal
     * sponsors=s1,s2,s3,s4,s5 // support up to 5 sponsors, currently only first 2 used
     * name=s   // driver/team name, not yet implemented but renders name on windshield
     * stampShow=d // optionally force club/sponsors off
     * 
     * // number, like a stamp but handled separately
     * numPat=d  // for fixed font cars use a -carId to render the correct font
     * numCol=x,x,x // hex as in FFFFFF,FFFFFF,FFFFFF
     * numSlnt=d // (0)normal, (1)left, (2)right, (3)forward, (4)back
     * number=s  //(ie 001, 23, etc)
     * numShow=d // turn off display of number
     * 
     * // car
     * carPath=s // rt2000, etc
     * carPat=d // web pattern number
     * carCol=x,x,x // hex colors
     * carCustPaint=s // full path to .tga file on disk, forces sponsor/club off and replaces web paint
     * 
     * // car wheel rim
     * carRimType=d // (0)matt, (1)chrome, (2)brushed aluminum, (3)glossy
     * carRimCol=x,x,x // hex colors
     * 
     * // suit
     * suitType=d // suit (body) model
     * suitPat=d  // web pattern number
     * suitCol=x,x,x // hex colors
     * suitCustPaint=s // full path to .tga file on disk
     * 
     * // helmet
     * hlmtType=d // helmet model
     * hlmtPat=d // web pattern number
     * hlmtCol=x,x,x // hex colors
     * hlmtCustPaint=s // full path to .tga file on disk
     * 
     * // face
     * faceType=d // face (head) model
     * 
     * 
     * Here are some sample calls.  
     * Note that on members you have to pass &view=1 to get my more advanced car rendering code to work, 
     * otherwise custom paints won't work.  
     * And for club/sponsors the view controls the stamp type (rectangle/square)
     * 
     * // Custom paint on a car
     * http://localhost:32034/pk_car.png?size=2&view=1&ca...nmartin%20dbr9\test_helmet.tga
     * 
     * // and the rest
     * http://localhost:32034/pk_car.png?size=0&view=1&li...FFFF&carRimType=2&carRimCol=3F
     * 
     * http://localhost:32034/pk_car.png?size=2&view=1&li...carRimCol=3FFF00,232323,FF0000
     * 
     * http://localhost:32034/pk_body.png?size=1&view=2&s...46699,000055,9900FF&faceType=6
     * 
     * http://localhost:32034/pk_suit.png?size=1&suitPat=...32100,FF77FF&view=0&suitType=0
     * 
     * http://localhost:32034/pk_helmet.png?size=2&hlmtPa...32100,FF77FF&view=0&hlmtType=0
     * 
     * http://localhost:32034/pk_head.png?size=1&view=0&s...03366,432100,FF77FF&faceType=6
     * 
     * http://localhost:32034/pk_number.png?view=1&number...2FFF00,FFFF00,00FF00&numSlnt=1
     * 
     * http://localhost:32034/pk_club.png?&club=13&view=0
     * 
     * http://localhost:32034/pk_sponsor.png?&sponsors=3&view=1
     * 
     * @see com.SIMRacingApps.Car#getImageUrl()
     */
    @Override
    public Data getImageUrl() {
        Data d = super.getImageUrl();
        d.setState(Data.State.OFF);

        try {
            if (isValid()) {
                //http://127.0.0.1:32034/car.png?dirpath=trucks%5Csilverado2015&size=2&pat=23&lic=feec04&car_number=1&colors=000000,cfcfcf,ff0600
    
                String UserID = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserID");
                String CarDesignStr = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarDesignStr");
                String CarNumberDesignStr = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
                
                if (CarDesignStr.isEmpty())
                    CarDesignStr = "0,000000,ffffff,666666";    //a suitable default
                
                if (CarNumberDesignStr.isEmpty())
                    CarNumberDesignStr = "0,0,000000,ffffff,666666";    //a suitable default
                
                String parts[] = CarDesignStr.split("[,;.-]");
                
                if (parts.length > 3) {
                    String dirpath = this.getName().getString().replace(" ","%5C");
                    String pat = parts[0];
                    String lic = String.format("%06x",this.getDriverLicenseColor().getInteger());
                    
                    String name = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getInteger("WeekendInfo","TeamRacing") > 0
                                ? this.getTeamName().getString().replace(" ", "%20")
                                : this.getDriverNameShort().getString().replace(" ", "%20");
                                
                    if (name.contains(","))
                        name = name.split(",")[0];
                    
                    String carSponser1 = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarSponsor_1");
                    String carSponser2 = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarSponsor_2");
                    String sponsors = (carSponser1.isEmpty() ? "0" : carSponser1) + "," + (carSponser2.isEmpty() ? "0" : carSponser2);
                    String club = _getClubNumber();
                    String colors = parts.length > 3 ? parts[1]+","+parts[2]+","+parts[3] : "";
                    String wheeltype = parts.length <= 4          ? ""   //default 
                                     : CarDesignStr.contains(";") ? "1"  //Chrome 
                                     : CarDesignStr.contains(".") ? "2"  //Brushed aluminum 
                                     : CarDesignStr.contains("-") ? "3"  //glossy
                                     :                              "0"; //default to mat, delimiter not recognized or a comma
                    String wheelColor = wheeltype.isEmpty() ? "" : parts[4];
                    
                    //had this commented out, but at some point iRacing defaults to showing the car number
                    //put it back so at least the colors are correct event if it is ignoring the carnumber passed in.
                    String numparts[] = CarNumberDesignStr.split("[,;.-]");
                    String car_number = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumber").replace("\"", "");
                    //String car_number = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberRaw").replace("\"", "");

                    String numfont   = "0";
                    String numslant  = "0";
                    String numcolors = "ffffff,777777,000000";
                    
                    if (numparts.length > 4) {
                        numfont   = numparts[0];
                        numslant  = numparts[1];
                        numcolors = numparts[2]+","+numparts[3]+","+numparts[4];
                    }
                    else 
                    if (numparts.length > 3) {
                        numfont   = numparts[0];
                        numcolors = numparts[1]+","+numparts[2]+","+numparts[3];
                    }
                    
//if (car_number.equals("39"))
//    car_number = car_number;

                    //the caller must replace iRacing with the hostname and port of the iRacing server
                    //if running on the same machine, then it is 127.0.0.1::32034
                    String url = "iRacing/car.png"
                            + "?dirpath="+dirpath
                            + "&size=2"
                            + "&pat="+pat
                            + "&lic="+lic
                            + "&car_number="+car_number
                            + "&carnumber="+car_number
                            + "&numPat="+numfont
                            + "&numfont="+numfont
                            + "&numslant="+numslant
                            + "&numSlnt="+numslant
                            + "&numcolors="+numcolors
                            + "&colors="+colors
                            + "&sponsors="+sponsors
                            + "&club="+club
//                            + (wheeltype.isEmpty() ? "" : "&wheels=" + wheeltype + "," + wheelColor)
                            + (wheeltype.isEmpty() ? "" : "&carRimType="+wheeltype)
                            + (wheeltype.isEmpty() ? "" : "&carRimCol="+wheelColor)
                    ;

                    //override the old url with the new one for non-custom paints as well
                    //did this to support the rim types
                    url = "iRacing/pk_car.png"
                            + "?view=1"
                            + "&size=2"
                            + "&carPath="+dirpath
                            + "&carPat="+pat
                            + "&carCol="+colors
                            + (lic.isEmpty() ? "" : "&licCol="+lic)
                            + "&number="+car_number
                            + "&numPat="+numfont
                            + "&numfont="+numfont
                            + "&numSlnt="+numslant
                            + "&numcol="+numcolors
                            + "&numShow="+(m_iRacingSIMPlugin.getIODriver().getHideCarNum() != 0 ? "0" : "1")
                            + (wheeltype.isEmpty() ? "" : "&carRimType="+wheeltype)
                            + (wheeltype.isEmpty() ? "" : "&carRimCol="+wheelColor)
                            + "&club="+club
                            + "&sponsors="+sponsors
                            + "&name="+name
                    ;

                    if (Server.getArg("show-custom-paint", true)) {  //added version 1.17
                        //if I can find the custom car paint file, use it
                        try {
                            FindFile customPaint = new FindFile(m_iRacingSIMPlugin.getIODriver().dataDir() + "\\paint" + "\\" + this.getName().getString() + "\\car_" + UserID + ".tga");
                            
                            // http://localhost:32034/pk_car.png?size=2&view=1&carPath=astonmartin\dbr9&number=32&carCustPaint=C:\Users\david\Documents\iRacing\paint\astonmartin%20dbr9\test_helmet.tga
                            url = "iRacing/pk_car.png"
                                    + "?view=1"
                                    + "&size=2"
                                    + "&carPath="+dirpath
                                    + "&number="+car_number
                                    + (lic.isEmpty() ? "" : "&licCol="+lic)
                                    + "&numPat="+numfont
                                    + "&numfont="+numfont
                                    + "&numSlnt="+numslant
                                    + "&numcol="+numcolors
                                    + (wheeltype.isEmpty() ? "" : "&carRimType="+wheeltype)
                                    + (wheeltype.isEmpty() ? "" : "&carRimCol="+wheelColor)
                                    + "&name="+name
                                    + "&carCustPaint="+customPaint.getFileFound().replace(" ", "%20").replace("\\", "\\\\")
                            ;
                        }
                        catch (FileNotFoundException e) {}
                        
                        //If user is hiding the car numbers, see if I can find a car_num file.
                        if (m_iRacingSIMPlugin.getIODriver().getHideCarNum() != 0
                        //&& !((iRacingSession)this.m_SIMPlugin.getSession())._isOfficial()
                        ) {
                            //if I can find the custom car paint file with a number, use it
                            try {
                                FindFile customPaint = new FindFile(m_iRacingSIMPlugin.getIODriver().dataDir() + "\\paint" + "\\" + this.getName().getString() + "\\car_num_" + UserID + ".tga");
                                
                                // http://localhost:32034/pk_car.png?size=2&view=1&carPath=astonmartin\dbr9&number=32&carCustPaint=C:\Users\david\Documents\iRacing\paint\astonmartin%20dbr9\test_helmet.tga
                                url = "iRacing/pk_car.png"
                                        + "?view=1"
                                        + "&size=2"
                                        + "&carPath="+dirpath
                                        + (lic.isEmpty() ? "" : "&licCol="+lic)
                                        //+ "&number="+car_number
                                        //+ "&numPat="+numfont
                                        //+ "&numfont="+numfont
                                        //+ "&numSlnt="+numslant
                                        //+ "&numcol="+numcolors
                                        + "&numShow=0"
                                        + (wheeltype.isEmpty() ? "" : "&carRimType="+wheeltype)
                                        + (wheeltype.isEmpty() ? "" : "&carRimCol="+wheelColor)
                                        + "&name="+name
                                        + "&carCustPaint="+customPaint.getFileFound().replace(" ", "%20").replace("\\", "\\\\")
                                ;
                            }
                            catch (FileNotFoundException e) {}
                        }
                        
                        //if a team even, then see if you have a team paint
                        //WeekendInfo.TeamRacing > 0
                        //DriverInfo.Drivers[idx].TeamID > 0
                        if (m_iRacingSIMPlugin.getIODriver().getSessionInfo().getInteger("WeekendInfo","TeamRacing") > 0) {
                            String teamID = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"TeamID");
                            if (!teamID.equals("0") && !teamID.isEmpty()) {
                                try {
                                    FindFile customPaint = new FindFile(m_iRacingSIMPlugin.getIODriver().dataDir() + "\\paint" + "\\" + this.getName().getString() + "\\car_team_" + teamID + ".tga");
                                    
                                    // http://localhost:32034/pk_car.png?size=2&view=1&carPath=astonmartin\dbr9&number=32&carCustPaint=C:\Users\david\Documents\iRacing\paint\astonmartin%20dbr9\test_helmet.tga
                                    url = "iRacing/pk_car.png"
                                            + "?view=1"
                                            + "&size=2"
                                            + "&carPath="+dirpath
                                            + (lic.isEmpty() ? "" : "&licCol="+lic)
                                            + "&number="+car_number
                                            + "&numPat="+numfont
                                            + "&numfont="+numfont
                                            + "&numSlnt="+numslant
                                            + "&numcol="+numcolors
                                            + (wheeltype.isEmpty() ? "" : "&carRimType="+wheeltype)
                                            + (wheeltype.isEmpty() ? "" : "&carRimCol="+wheelColor)
                                            + "&name="+name
                                            + "&carCustPaint="+customPaint.getFileFound().replace(" ", "%20").replace("\\", "\\\\")
                                    ;
                                }
                                catch (FileNotFoundException e) {}
                            }
                        }                    
                        
                        if (m_iRacingSIMPlugin.getIODriver().getHideCarNum() != 0
                        //&& !((iRacingSession)this.m_SIMPlugin.getSession())._isOfficial()
                        ) {
                            if (m_iRacingSIMPlugin.getIODriver().getSessionInfo().getInteger("WeekendInfo","TeamRacing") > 0) {
                                String teamID = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"TeamID");
                                if (!teamID.equals("0") && !teamID.isEmpty()) {
                                    //if I can find the custom car paint file with a number, use it
                                    try {
                                        FindFile customPaint = new FindFile(m_iRacingSIMPlugin.getIODriver().dataDir() + "\\paint" + "\\" + this.getName().getString() + "\\car_team_num_" + teamID + ".tga");
                                        
                                        // http://localhost:32034/pk_car.png?size=2&view=1&carPath=astonmartin\dbr9&number=32&carCustPaint=C:\Users\david\Documents\iRacing\paint\astonmartin%20dbr9\test_helmet.tga
                                        url = "iRacing/pk_car.png"
                                                + "?view=1"
                                                + "&size=2"
                                                + "&carPath="+dirpath
                                                + (lic.isEmpty() ? "" : "&licCol="+lic)
                                                //+ "&number="+car_number
                                                //+ "&numPat="+numfont
                                                //+ "&numfont="+numfont
                                                //+ "&numSlnt="+numslant
                                                //+ "&numcol="+numcolors
                                                + "&numShow=0"
                                                + (wheeltype.isEmpty() ? "" : "&carRimType="+wheeltype)
                                                + (wheeltype.isEmpty() ? "" : "&carRimCol="+wheelColor)
                                                + "&name="+name
                                                + "&carCustPaint="+customPaint.getFileFound().replace(" ", "%20").replace("\\", "\\\\")
                                        ;
                                    }
                                    catch (FileNotFoundException e) {}
                                }
                            }
                        }
                    }
                    
                    if (!m_url.equals(url)) {
                        Server.logger().fine("image url="+m_url);
                        m_url = url;
                    }
                    d.setValue(m_url);
                    d.setState(Data.State.NORMAL);
                }
            }
        } catch (NumberFormatException e) {}
        
        return d;
    }
    
    @Override
    public Data getIncidents() {
        Data d = super.getIncidents();
//        if (isME()) {
//            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("PlayerCarDriverIncidentCount") != null) {
//                d.setState(Data.State.OFF);
//    
//                if (isValid()) {
//                    int i = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("PlayerCarDriverIncidentCount");
//                    if (i >= 0)
//                        m_lastKnownIncidents = i;
//                    d.setValue(m_lastKnownIncidents,d.getUOM(),Data.State.NORMAL);
//                }
//            }
//        }
//        else {
//            //if we've seen any incidents from other cars, either end of race or admin
//            if (((iRacingSession)m_iRacingSIMPlugin.getSession())._getHasIncidents()) {
//                d.setValue(m_results.getIncidents(),d.getUOM(),Data.State.NORMAL);
//            }
//        }
        if (isME() || ((iRacingSession)m_iRacingSIMPlugin.getSession())._getHasIncidents()) {
            d.setValue(m_driverIncidents,d.getUOM(),Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getIncidentsTeam() {
        Data d = super.getIncidentsTeam();
        if (m_iRacingSIMPlugin.getIODriver().getSessionInfo().getInteger("WeekendInfo","TeamRacing") > 0) {
            //if we've seen any incidents from other cars, either end of race or admin
            if (isME() || ((iRacingSession)m_iRacingSIMPlugin.getSession())._getHasIncidents()) {
                d.setValue(m_teamIncidents,d.getUOM(),Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getIsBlackFlag() {
        Data d = super.getIsBlackFlag();
        if (isValid() && isME()) {
            int flags = m_iRacingSIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.black) != 0 || (flags & SessionFlags.repair) != 0)
                d.setValue(true);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getIsBlueFlag() {
        Data d = super.getIsBlueFlag();
        if (isValid() && isME()) {
            int flags = m_iRacingSIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.blue) != 0)
                d.setValue(true);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getIsDisqualifyFlag() {
        Data d = super.getIsDisqualifyFlag();
        if (isValid() && isME()) {
            int flags = m_iRacingSIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.disqualify) != 0)
                d.setValue(true);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getIsDriving() {
        Data d = super.getIsDriving();
        if (isValid() && isME()) {
            boolean isOnTrack = m_iRacingSIMPlugin.getIODriver().getVars().getBoolean("IsOnTrack");
            d.setValue(isOnTrack,"boolean",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getIsFixedSetup() {
        Data d = super.getIsFixedSetup();
        d.setState(Data.State.OFF);
        try {
            if (isValid()) {
                String fixed = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","IsFixedSetup");
                if (Integer.parseInt(fixed) == 1)
                    d.setValue(true);
                else
                    d.setValue(false);
                d.setState(Data.State.NORMAL);
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public boolean isME() {
        if (isValid())
            return m_id == m_ME;
        return false;
    }
    
    @Override
    public Data getIsPitSpeedLimiter() {
        Data d = super.getIsPitSpeedLimiter();
        if (isValid() && isME()) {  //TODO: Is there a way to determine if this car has a limiter?
            d.setState(Data.State.NORMAL);
            
            if (getWarnings().getString().contains(";PITSPEEDLIMITER;"))
                d.setValue(true,"boolean");
        }
        return d;
    }

    @Override
    public Data getIsSpectator() {
        Data d = super.getIsSpectator();
        d.setState(Data.State.OFF);
        try {
            if (isValid()) {
                String spectator = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"IsSpectator");
                if (spectator.length() > 0 && Integer.parseInt(spectator) == 1)
                    d.setValue(true);
                else
                    d.setValue(false);
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public Data getIsPaceCar() {
        Data d = super.getIsPaceCar();
        d.setState(Data.State.OFF);
        if (isValid()) {
            d.setValue(isPaceCar(),"boolean",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getIsYellowFlag() {
        Data d = super.getIsYellowFlag();
        if (isValid() && isME()) {
            int flags = m_iRacingSIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.yellow) != 0 || (flags & SessionFlags.yellowWaving) != 0)
                d.setValue(true);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getLap(String ref,int lapsToAverage) {
        Data d = super.getLap(ref,lapsToAverage);
        d.setState(Data.State.OFF);
        
        String r = d.getValue("reference").toString();

        if (r.equals(Car.LapType.AVERAGE)) {
            int c = 0;
            int laps = m_results.getLapTimes().size();
            if (lapsToAverage > 0) {
                //now take the average of the last "laps" laps that were not invalid laps
                //if we don't have enough valid "laps", then use as many as you have.
                for (int i=0; c < lapsToAverage && i < laps; i++) {
                    if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                        c++;
                    }
                }
            }
            if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                d.setValue(c);
                d.setState(Data.State.NORMAL);
            }
        }
        else
        if (r.equals(Car.LapType.RUNNINGAVERAGE)) {
            int c = 0;
            if (lapsToAverage > 0) {
                //now take the average of the last "laps" laps that were not invalid laps
                //if we don't have enough valid "laps", then use as many as you have.
                for (int i=m_results.getLapTimes().size()-1; c < lapsToAverage && i >= 0; i--) {
                    if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                        c++;
                    }
                }
            }
            if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                d.setValue(c);
                d.setState(Data.State.NORMAL);
            }
        }
        else
        if (r.equals(Car.LapType.AVERAGESINCEPITTING)) {
            int c = 0;
            int laps = m_results.getLapTimes().size();
            
            if (!m_averageSincePittingLaps.isEmpty() && getStatus().getString().equals(Car.Status.LEAVINGPITS))
                m_averageSincePittingLaps = new HashMap<Integer,Integer>();
            
            if (lapsToAverage > 0) {
                //now take the average of the last "laps" laps that were not invalid laps
                //if we don't have enough valid "laps", then use as many as you have.
                for (int i=m_lapPitted-1; c < lapsToAverage && i < laps; i++) {
                    if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                        c++;
                    }
                }
            }
            if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                d.setValue(c);
                d.setState(Data.State.NORMAL);
                m_averageSincePittingLaps.put(lapsToAverage, c);
            }
            else
            if (m_averageSincePittingLaps.containsKey(lapsToAverage)) {
                d.setValue(m_averageSincePittingLaps.get(lapsToAverage));
                d.setState(Data.State.NORMAL);
            }
        }
        else
        if (r.equals(Car.LapType.RUNNINGAVERAGESINCEPITTING)) {
            int c = 0;
            
            if (!m_runningAverageSincePittingLaps.isEmpty() && getStatus().getString().equals(Car.Status.LEAVINGPITS))
                m_runningAverageSincePittingLaps = new HashMap<Integer,Integer>();
            
            if (lapsToAverage > 0) {
                //now take the average of the last "laps" laps that were not invalid laps
                //if we don't have enough valid "laps", then use as many as you have.
                for (int i=m_results.getLapTimes().size()-1; c < lapsToAverage && i >= 0 && i >= (m_lapPitted-1); i--) {
                    if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                        c++;
                    }
                }
            }
            if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                d.setValue(c);
                d.setState(Data.State.NORMAL);
                m_runningAverageSincePittingLaps.put(lapsToAverage, c);
            }
            else
            if (m_runningAverageSincePittingLaps.containsKey(lapsToAverage)) {
                d.setValue(m_runningAverageSincePittingLaps.get(lapsToAverage));
                d.setState(Data.State.NORMAL);
            }
        }
        else
        if (r.equals(Car.LapType.CURRENT)) {
            d.setValue(m_lapCompleted+1);
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.SESSIONBEST)) {
            d.setValue(m_results.getLapBest());
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.QUALIFYING)) {
            if (m_sessionType.equals(Session.Type.LONE_QUALIFY) || m_sessionType.equals(Session.Type.OPEN_QUALIFY))
                d.setValue(m_results.getLapBest());
            else
                d.setValue(m_resultsQualifying.getLapBest());
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.COMPLETED) || r.equals(Car.LapType.SESSIONLAST)) {
            d.setValue(m_lapCompleted);
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.LED)) {
            d.setValue(m_results.getLapsLed());
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.COMPLETEDPERCENT)) {
            d.setValue(m_lapCompletedPercent * 100.0);
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.PITTED)) {
            d.setValue(m_lapPitted);
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.SINCEPITTING)) {
            if (Server.getArg("laps-since-pitting-valid-only", false)) {
                int laps = m_results.getLapTimes().size();
                int c = 0;
                for (int i=m_lapPitted-1; i < laps; i++) {
                    if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                        c++;
                    }
                }
                d.setValue(c);
            }
            else {
                d.setValue((m_lapCompleted + 1) - m_lapPitted + 1);
            }
            if (d.getInteger() > m_lapCompleted + 1)
                d.setValue(m_lapCompleted + 1);
            d.setState(Data.State.NORMAL);
        }
        else
        if (r.equals(Car.LapType.CAUTION)) {
            d.setValue(m_cautionLaps);
            d.setState(Data.State.NORMAL);
        }

        return d;
    }

    @Override
    public Data getLapsToGo() {
        Data d = super.getLapsToGo();
        d.setState(Data.State.OFF);
        if (m_iRacingSIMPlugin.isConnected()) {
            boolean isRace = m_iRacingSIMPlugin.getSession().getType().getString().equalsIgnoreCase(Type.RACE);
            boolean isQual = m_iRacingSIMPlugin.getSession().getType().getString().equalsIgnoreCase(Type.LONE_QUALIFY)
                          || m_iRacingSIMPlugin.getSession().getType().getString().equalsIgnoreCase(Type.OPEN_QUALIFY);
            
            //if this new value is in the telemetry, then return it. Otherwise fall back to calculating it.
            //It was added in the Dec 8, 2015 build. There was a variable called SessionLapsRemain that I never used in the previous build.
            /* This was overriding the ability to get the laps remaining based on time remaining. I will do it myself
            if (isRace && m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("SessionLapsRemainEx") != null) {
                int sessionLapsRemainEx = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("SessionLapsRemainEx");
                d.setValue(sessionLapsRemainEx);
                d.setState(Data.State.NORMAL);
                return d;
            }
            */
            
            int laps       = m_iRacingSIMPlugin.getSession().getLaps().getInteger();
            int lap        = getLap(iRacingCar.LapType.COMPLETED).getInteger();
            int togo       = isRace || isQual ? laps - lap : laps;
            
            //I don't think the white comes out for the leader, it waits until you are close to line
            //by contrast, if you cross the line and the white is out, everyone behind you will get a premature 1 togo.
            //But, this is the best we can do.
            if ((m_sessionFlags & SessionFlags.checkered) != 0) {
                togo = 0;
            }
            else
            if ((m_sessionFlags & SessionFlags.white) != 0) {
                togo = 1;
            }
            else {
            
                //if the session is time bound, then calculate the remaining laps based on the average lap time
//This doesn't return the actual time remaining, so using the Var version                
//double timeRemaining = m_iRacingSIMPlugin.getSession().getTimeRemaining().getDouble();
                double timeRemaining = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("SessionTimeRemain");
                
                //used for testing. Set the arg to the duration of the session in seconds
                timeRemaining = Math.min(timeRemaining, Server.getArg("iracing-sessionduration",timeRemaining + m_sessionTime) - m_sessionTime); //for testing

                if (timeRemaining < 604800.0) {  //this is the number returned when remaining is unlimited

                    ArrayList<Double>  lapTimes   = m_results.getLapTimes();
                    
                    double total = 0.0;
                    int c = 0;
                    int lapsToAverage = 2;
                    
                    //average the laps, don't count lap 1
                    for (int i=lapTimes.size()-1; c < lapsToAverage && i > 0; i--) {
                        if (i < m_invalidLaps.size() && !m_invalidLaps.get(i) && lapTimes.get(i) > 0.0) {
                            total += lapTimes.get(i);
                            c++;
                        }
                    }
                    
                    if (c > 0) {
                        double averageTime = total / c;
                        
                        if (timeRemaining <= 0.0) {
                            timeRemaining = 0.0;

                            //add a lap for the final lap if you are in a limited lap session
                            if (isRace) // && laps != Session.UNLIMITEDLAPS)
                                togo = 2;  //white flag will override this.
                            else
                                togo = 0;
                        }
                        else {
                            double percentCompleted = getLap(LapType.COMPLETEDPERCENT).getDouble() / 100.0;
                            timeRemaining += (averageTime * percentCompleted);
                            //add a lap for the final lap if you are in a limited lap session
                            //take the minimum of the calculated laps and the actual number of laps
                            int calculatedTogo;
                            if (isRace && laps != Session.UNLIMITEDLAPS)
                                calculatedTogo = (int)(Math.floor(timeRemaining / averageTime) + 2.0);
                            else
                                calculatedTogo = (int)(Math.floor(timeRemaining / averageTime) + 1.0);
                            
                            if (calculatedTogo < togo) {
                                togo = calculatedTogo;
                                d.setUOM("~lap");
                            }
                        }
                    }
                    else {
                        //if session has unlimited laps, then show as approximate
                        if (laps == Session.UNLIMITEDLAPS) {
                            togo = 0;
                            d.setUOM("~lap");
                        }
                    }
                }
            }

            //apply some boundaries to the result
            togo = (int)Math.min(laps,Math.min(Math.max(0, togo),isRace || isQual ? laps - lap : laps));

            d.setValue(togo);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getLapTime(String ref, int lapsToAverage) {
        Data d = super.getLapTime(ref,lapsToAverage);
        d.setState(Data.State.OFF);
        String r = d.getValue("reference").toString();

        if (isValid()) {
            if (r.equals(Car.LapType.AVERAGE)) {
                ArrayList<Double> lapTimes = m_results.getLapTimes();
                double average = 0.0;
                int c = 0;
                int laps = lapTimes.size();
                if (lapsToAverage > 0) {
                    //now take the average of the last "laps" laps that were not invalid laps
                    //if we don't have enough valid "laps", then use as many as you have.
                    for (int i=0; c < lapsToAverage && i < laps; i++) {
                        if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                            average += lapTimes.get(i);
                            c++;
                        }
                    }
                }
                if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                    d.setValue(average / (double)c);
                    d.setState(Data.State.NORMAL);
                }
            }
            else
            if (r.equals(Car.LapType.RUNNINGAVERAGE)) {
                ArrayList<Double> lapTimes = m_results.getLapTimes();
                double average = 0.0;
                int c = 0;
                if (lapsToAverage > 0) {
                    //now take the average of the last "laps" laps that were not invalid laps
                    //if we don't have enough valid "laps", then use as many as you have.
                    for (int i=lapTimes.size()-1; c < lapsToAverage && i >= 0; i--) {
                        if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                            average += lapTimes.get(i);
                            c++;
                        }
                    }
                }
                if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                    d.setValue(average / (double)c);
                    d.setState(Data.State.NORMAL);
                }
            }
            else
            if (r.equals(Car.LapType.AVERAGESINCEPITTING)) {
                ArrayList<Double> lapTimes = m_results.getLapTimes();
                double average = 0.0;
                int c = 0;
                int laps = lapTimes.size();
                
                if (!m_averageSincePittingTime.isEmpty() && getStatus().getString().equals(Car.Status.LEAVINGPITS))
                    m_averageSincePittingTime = new HashMap<Integer,Double>();

                if (lapsToAverage > 0) {
                    //now take the average of the last "laps" laps that were not invalid laps
                    //if we don't have enough valid "laps", then use as many as you have.
                    for (int i=m_lapPitted-1; c < lapsToAverage && i < laps; i++) {
                        if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                            average += lapTimes.get(i);
                            c++;
                        }
                    }
                }
                if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                    d.setValue(average / (double)c);
                    d.setState(Data.State.NORMAL);
                    m_averageSincePittingTime.put(lapsToAverage, d.getDouble());
                }
                else
                if (m_averageSincePittingTime.containsKey(lapsToAverage)) {
                    d.setValue(m_averageSincePittingTime.get(lapsToAverage));
                    d.setState(Data.State.NORMAL);
                }
            }
            else
            if (r.equals(Car.LapType.RUNNINGAVERAGESINCEPITTING)) {
                ArrayList<Double> lapTimes = m_results.getLapTimes();
                double average = 0.0;
                int c = 0;
                
                if (!m_runningAverageSincePittingTime.isEmpty() && getStatus().getString().equals(Car.Status.LEAVINGPITS))
                    m_runningAverageSincePittingTime = new HashMap<Integer,Double>();

                if (lapsToAverage > 0) {
                    //now take the average of the last "laps" laps that were not invalid laps
                    //if we don't have enough valid "laps", then use as many as you have.
                    for (int i=lapTimes.size()-1; c < lapsToAverage && i >= 0 && i >= (m_lapPitted-1); i--) {
                        if (i < m_invalidLaps.size() && !m_invalidLaps.get(i)) {
                            average += lapTimes.get(i);
                            c++;
                        }
                    }
                }
                if (c > 0 && (c == lapsToAverage || lapsToAverage == 9999)) {
                    d.setValue(average / (double)c);
                    d.setState(Data.State.NORMAL);
                    m_runningAverageSincePittingTime.put(lapsToAverage, d.getDouble());
                }
                else
                if (m_runningAverageSincePittingTime.containsKey(lapsToAverage)) {
                    d.setValue(m_runningAverageSincePittingTime.get(lapsToAverage));
                    d.setState(Data.State.NORMAL);
                }
            }
            else
            if (r.equals(Car.LapType.CURRENT)) {
                double timeAtStartFinish = this.m_timeAtStartFinish.size() > 0 ? this.m_timeAtStartFinish.get(this.m_timeAtStartFinish.size()-1) : 0.0;
                
//TODO: Use LapCurrentLapTime. Currently it doesn't reset until 1 to 2 seconds after you cross the line
                if (!(timeAtStartFinish > 0.0 || m_sessionStartTime > 0.0) && isME()) {
                    double laptime = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("LapCurrentLapTime");
                    d.setValue(laptime);
                    d.setUOM(m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapCurrentLapTime").Unit);
                    d.setState(Data.State.NORMAL);
                }
                else
                //use the current session time minus the time when they last crossed the line.
                if (timeAtStartFinish > 0.0 || m_sessionStartTime > 0.0) {
                    //time At Start Finish is relative to the session start time
                    d.setValue(m_sessionTime - (this.m_sessionStartTime + timeAtStartFinish),"s",Data.State.NORMAL);
                }
                else
                //use the last lap time to estimate for other cars
                if (m_results.getLapTimeLast() > 0.0) {
                    double laptime = m_results.getLapTimeLast() * m_lapCompletedPercent;
                    d.setValue(laptime);
                    d.setUOM(m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapCurrentLapTime").Unit);
                    d.setState(Data.State.NORMAL);
                }
            }
            else
            if (r.equals(Car.LapType.BEST)) {
                //TODO: Can we use DeltaBest to derive the overall best lap?
                //d.setValue(m_results.getLapTimeBest());
            }
            else
            if (r.equals(Car.LapType.RACESTART)) {
                d.setValue(m_sessionStartTime);
            }
            else
            if (r.equals(Car.LapType.FINISHLINE)) {
                if (m_sessionEndTime > 0.0)
                    d.setValue(m_sessionEndTime);
                else
                    d.setValue(m_timeAtStartFinish.size() > 0 ? m_timeAtStartFinish.get(m_timeAtStartFinish.size()-1) : 0.0);
            }
            else
            if (r.equals(Car.LapType.REMAININGFINISHLINE)) {
                d.setValue(this.m_timeRemainingAtStartFinish);
            }
            else
            if (r.equals(Car.LapType.SESSIONBEST)) {
                d.setValue(m_results.getLapTimeBest());
            }
            else
            if (r.equals(Car.LapType.QUALIFYING)) {
                if (m_sessionType.equals(Session.Type.LONE_QUALIFY) || m_sessionType.equals(Session.Type.OPEN_QUALIFY))
                    d.setValue(m_results.getLapTimeBest());
                else
                    d.setValue(m_resultsQualifying.getLapTimeBest());
            }
            else
            if (r.equals(Car.LapType.SESSIONLAST) || r.equals(Car.LapType.SESSIONLAST)) {
                d.setValue(m_results.getLapTimeLast());
            }
        }

        return d;
    }

    @Override
    public Data getLapTimeDelta(String ref) {
        Data d = super.getLapTimeDelta(ref);

        if (isME()) {
            d.setState(Data.State.OFF);
            String r = d.getValue("reference").toString();

            //spelling error in the variable name
            if (r.equals("SessionLast")) {
                //see if the bad name exists and use it. This will auto correct when it gets fixed
                if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapDeltaTo"+r+"lLap_OK") != null)
                    r = "SessionLastl";
            }

            if (m_iRacingSIMPlugin.getIODriver().getVars().getBoolean("LapDeltaTo"+r+"Lap_OK")) {
                double laptime = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("LapDeltaTo"+r+"Lap");
                d.setValue(laptime);
                d.setState(Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getLapTimeDeltaReference(String ref) {
        Data d = super.getLapTimeDeltaReference(ref);
        String r = d.getValue("reference").toString();
        Data t;
        //iRacing doesn't give me the reference value for all of them, so for the ones that don't use SessionBest
        if (r.equals("SessionLast")) {
            t = getLapTime(r);
            d.setValue(t.getValue(),t.getUOM(),t.getState());
        }
        else {
            d.setValue(m_results.getLapTimeBestCleanDriver(getDriverName(false).getString()));
            if (d.getDouble() > 0.0)
                d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getLapTimeDeltaPercent(String ref) {
        Data d = super.getLapTimeDeltaPercent(ref);
        if (isME()) {
            String r = d.getValue("reference").toString();
            d.setState(Data.State.OFF);

            //spelling error in the variable name
            if (r.equals("SessionLast")) {
                //see if the bad name exists and use it. This will auto correct when it gets fixed
                if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapDeltaTo"+r+"lLap_OK") != null)
                    r = "SessionLastl";
            }

            if (m_iRacingSIMPlugin.getIODriver().getVars().getBoolean("LapDeltaTo"+r+"Lap_OK")) {
                double laptime = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("LapDeltaTo"+r+"Lap_DD");
                laptime *= 4000.0;
                if (laptime > 100.0)
                    laptime = 100.0;
                else
                if (laptime < -100)
                    laptime = -100.0;
                d.setValue(laptime);
                d.setState(Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getLapTimes() {
        Data d = super.getLapTimes();
        d.setValue(m_results.getLapTimes());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getLapInvalidFlags() {
        Data d = super.getLapTimes();
        d.setValue(m_invalidLaps);
        d.setState(Data.State.NORMAL);
        return d;
    }
    
    /**
     * iRacing only outputs this value in the IBT files for ME
     * This code checks to see if it exists before returning it, otherwise it calls the base class.
     */
    @Override
    public Data getLatitude(String UOM) {
        if (Server.getArg("iracing-uselatlon",true) && isME()) { //allow an option to not use the iRacing Lat/Lon for testing
            VarHeader Lat = m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("Lat",m_iRacingSIMPlugin.getIODriver().getVars());
            if (Lat != null)
                return new Data("/Car/I"+m_id+"/Latitude",Lat.Value,Lat.Unit,Data.State.NORMAL);
        }
        return super.getLatitude(UOM);
    }
    
    @Override
    public Data getLatitudeAcceleration(String UOM) {
        Data d = super.getLatitudeAcceleration(UOM);
        if (isME()) {
            double accel = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("LatAccel");
            d.setValue(accel,"m/s2",Data.State.NORMAL);
        }
        return d.convertUOM(UOM);
    }

    @Override
    public Data getLongitudeAcceleration(String UOM) {
        Data d = super.getLongitudeAcceleration(UOM);
        if (isME()) {
            double accel = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("LongAccel");
            d.setValue(accel,"m/s2",Data.State.NORMAL);
        }
        return d.convertUOM(UOM);
    }
    
    /**
     * iRacing only outputs this value in the IBT files for ME.
     * This code checks to see if it exists before returning it, otherwise it calls the base class.
     */
    @Override
    public Data getLongitude(String UOM) {
        if (Server.getArg("iracing-uselatlon",true) && isME()) { //allow an option to not use the iRacing Lat/Lon for testing
            VarHeader Lat = m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("Lon",m_iRacingSIMPlugin.getIODriver().getVars());
            if (Lat != null)
                return new Data("/Car/I"+m_id+"/Longitude",Lat.Value,Lat.Unit,Data.State.NORMAL);
        }
        return super.getLongitude(UOM);
    }

    @Override
    public Data getMaxTires() {
        Data d = super.getMaxTires();
        //If the parent has not found any max tires through the plugin logic, then use one of the tires from iRacing
        if (!d.getState().equals(Data.State.NORMAL) && Server.getArg("use-iRacing-tire-limit", true)) {
            if (m_iRacingSIMPlugin.isConnected()) {
                String sessionType = m_SIMPlugin.getSession().getType().getString();
                if (sessionType.equals(Session.Type.RACE) && isME()) {
                    int maxTires = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("PlayerCarDryTireSetLimit");

                    if (maxTires > 0) {
                        d.setValue(maxTires);
                        d.setState(Data.State.NORMAL);
                    }
                }
            }
        }
        return d;
    }

    @Override
    public Data getMergePoint() {
        Data d = super.getMergePoint();
        d.setValue(m_mergePoint * 100.0);
        d.setState(Data.State.NORMAL);
        return d;
    }
    
    @Override
    public Data getMessages() {
        Data d = super.getMessages();
        StringBuffer flagnames = new StringBuffer("");
        
        if (m_iRacingSIMPlugin.isConnected()) {
            if (isME()) {
                int flags = m_iRacingSIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
                if (flags == -1)
                    flags = 0;
                int pitStatus = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("PlayerCarPitSvStatus");
                if (pitStatus == -1)
                    pitStatus = PitSvStatus.None;
                
    
                if ((flags & SessionFlags.repair) != 0)  { flagnames.append(";"); flagnames.append(Car.Message.REPAIR); }
                if (getIsPitSpeedLimiter().getBoolean()) { flagnames.append(";"); flagnames.append(Car.Message.PITSPEEDLIMITER); }
                double towtime = 0.0;
                if (isME() && (towtime = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("PlayerCarTowTime")) > 0.0) {  //June 2018
                    flagnames.append(";"); flagnames.append(Car.Message.TOWING + " " + String.format("%.0f:%02.0f", Math.floor(towtime / 60.0), towtime % 60.0));
                }
    
                if (pitStatus == PitSvStatus.BadAngle)      { flagnames.append(";"); flagnames.append(Car.Message.STRAIGHTENUP); }
                if (pitStatus == PitSvStatus.CantFixThat)   { flagnames.append(";"); flagnames.append(Car.Message.TOOMUCHDAMAGE); }
                if (pitStatus == PitSvStatus.InProgress)    { flagnames.append(";"); flagnames.append(Car.Message.PITSERVICEINPROGRESS); }
                if (pitStatus == PitSvStatus.TooFarBack)    { flagnames.append(";"); flagnames.append(Car.Message.TOOFARBACK); }
                if (pitStatus == PitSvStatus.TooFarForward) { flagnames.append(";"); flagnames.append(Car.Message.TOOFARFORWARD); }
                if (pitStatus == PitSvStatus.TooFarLeft)    { flagnames.append(";"); flagnames.append(Car.Message.TOOFARLEFT); }
                if (pitStatus == PitSvStatus.TooFarRight)   { flagnames.append(";"); flagnames.append(Car.Message.TOOFARRIGHT); }
                
            }
            else {
                //only these messages are available for other cars
                if (m_prevStatus.equals(iRacingCar.Status.INPITSTALL)) { flagnames.append(";"); flagnames.append(Car.Message.PITSERVICEINPROGRESS); }
            }
        }
        
        if (flagnames.length() > 0) {
            flagnames.append(";");
            d.setValue(flagnames.toString());
        }
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getNumber() {
        Data d = super.getNumber();
        if (isPaceCar())
            d.setValue(getDriverInitials().getString());
        else
            d.setValue(m_number);
        d.setState(Data.State.NORMAL);
        return d;
    }

    /**
     * Returns the integer version of the car number to be used with camera commands.
     * 
     * @return The raw car number as an integer
     */
    public int getNumberRaw() {
        if (m_numberRaw > -1)
            return m_numberRaw;
        if (!m_number.isEmpty())
            return Integer.parseInt(m_number);
        return -1;
    }
    
    private static FindFile m_fontnames = null;
        
    @Override
    public Data getNumberFont() {
        Data d = super.getNumberFont();
        d.setState(Data.State.OFF);

        if (m_fontnames == null) {
            try {
                m_fontnames = new FindFile("com/SIMRacingApps/SIMPlugins/iRacing/FontNames.json");
            } catch (FileNotFoundException e) {
                Server.logStackTrace(e);
            }
        }
        
        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The font of the number is the 1st number.
        if (isValid() && !isPaceCar() && m_fontnames != null) {
            String design = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
            String s[]    = design.split("[,;.-]");
            if (s.length > 0) {
//s[0] = "35";                
                String font_string = (String) m_fontnames.getJSON().get(s[0]);
                
                if (font_string != null && !font_string.isEmpty())
                    d.setValue(font_string,"",Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getNumberSlant() {
        Data d = super.getNumberSlant();
        d.setState(Data.State.OFF);

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The slant of the number is the 2rd number.
        //0=normal, 1=left, 2=right, 3=forward, 4=backwards
        if (isValid() && !isPaceCar()) {
            String design = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
            String s[]    = design.split("[,;.-]");
            if (s.length > 1) {
                String slant_string = "normal";
                try {
                    int slant = Integer.parseInt(s.length == 4 ? s[0] : s[1]);
                    if (slant == 1)
                        slant_string = "left";
                    else
                    if (slant == 2)
                        slant_string = "right";
                    else
                    if (slant == 3)
                        slant_string = "forward";
                    else
                    if (slant == 4)
                        slant_string = "backwards";
                }
                catch (NumberFormatException e) {}
                d.setValue(slant_string,"",Data.State.NORMAL);
            }
        }
        return d;
    }
    
    @Override
    public Data getPitTime() {
        Data d = super.getPitTime();
        d.setState(Data.State.OFF);
        //the times reported are typically .5 seconds too high because of the time it takes to get out of the stall. 
        //So just removing that to get closer to what iRacing says.
        int pitted = m_lapPitted -1;
        if (pitted >= 0 && pitted < m_pitTimes.size()) {
            d.setValue(m_pitTimes.get(pitted));
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getPitTimes() {
        Data d = super.getPitTimes();
        d.setValue(m_pitTimes,"s",Data.State.NORMAL);
        return d;
    }
    
    @Override
    public Data getPitLocation() {
        Data d = super.getPitLocation();
        d.setState(Data.State.OFF);
        if (m_pitLocation >= 0.0)
            d.setValue(m_pitLocation * 100.0,"%",Data.State.NORMAL);
        return d;
    }
    
    @Override
    public Data getPosition() {
        Data d = super.getPosition();
        d.setValue(m_results.getPosition());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPositionHighest() {
        Data d = super.getPositionHighest();
        d.setValue(m_results.getPositionHighest());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPositionLowest() {
        Data d = super.getPositionLowest();
        d.setValue(m_results.getPositionLowest());
        d.setState(Data.State.NORMAL);
        return d;
    }
    
    @Override
    public Data getPositions() {
        Data d = super.getPositions();
        d.setValue(m_results.getPositions());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPositionClass() {
        Data d = super.getPositionClass();
        d.setValue(m_results.getPositionClass());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPositionHighestClass() {
        Data d = super.getPositionHighestClass();
        d.setValue(m_results.getPositionHighestClass());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPositionLowestClass() {
        Data d = super.getPositionLowestClass();
        d.setValue(m_results.getPositionLowestClass());
        d.setState(Data.State.NORMAL);
        return d;
    }
    
    @Override
    public Data getPositionsClass() {
        Data d = super.getPositionsClass();
        d.setValue(m_results.getPositionsClass());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPositionQualifying() {
        Data d = super.getPositionQualifying();
        d.setValue(m_resultsQualifying.getPosition());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPositionClassQualifying() {
        Data d = super.getPositionClassQualifying();
        d.setValue(m_resultsQualifying.getPositionClass());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getPushToPassRemaining() {
        Data d = super.getPushToPassRemaining();
        d.setState(Data.State.OFF);
        if (isValid()) {
            if (isME()) {
                VarHeader P2P_Count = m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("P2P_Count",m_iRacingSIMPlugin.getIODriver().getVars());
                if (P2P_Count != null) {
                    d.setValue(P2P_Count.Value,"s");
                    d.setState(Data.State.NORMAL);
                }
            }
        }
        return d;
    }

    @Override
    public Data getIsPushToPassActive() {
        Data d = super.getIsPushToPassActive();
        d.setState(Data.State.OFF);
        if (isValid()) {
            if (isME()) {
                VarHeader P2P_Status = m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("P2P_Status",m_iRacingSIMPlugin.getIODriver().getVars());
                if (P2P_Status != null) {
                    d.setValue((boolean)P2P_Status.Value,"boolean");
                    d.setState(Data.State.NORMAL);
                }
            }
        }
        return d;
    }

    
    @Override
    public Data getRadioChannel() {
        Data d = super.getRadioChannel();
        d.setState(Data.State.OFF);
        if (isValid()) {
            if (isME()) {
                String s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","SelectedRadioNum");
                s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios",s,"TunedToFrequencyNum");
                if (!s.isEmpty())
                    d.setValue(Integer.parseInt(s));
            }
            else {
                d.setValue(m_lastKnownFrequency);
            }
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getRadioChannelName() {
        Data d = super.getRadioChannelName();
        d.setState(Data.State.OFF);
        if (isValid()) {
//              RadioInfo:
//              SelectedRadioNum: 0
//              Radios:
//              - RadioNum: 0
//                HopCount: 1
//                NumFrequencies: 5
//                TunedToFrequencyNum: 0
//                ScanningIsOn: 1
//                Frequencies:
//                - FrequencyNum: 0
//                  FrequencyName: "@DRIVERS"
//                  Priority: 15
//                  CarIdx: -1
//                  EntryIdx: -1
//                  ClubID: 0
//                  CanScan: 1
//                  CanSquawk: 1
//                  Muted: 0
//                  IsMutable: 0
//                  IsDeletable: 0
            
            String selectedRadio = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","SelectedRadioNum");
            int idx = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("RadioTransmitCarIdx");

            String teamspeakName = "";
            try {
                teamspeakName = m_SIMPlugin.getData("TeamSpeak/Talker").getString();
            } catch (SIMPluginException e1) {
            }

            //see if we can detect if anyone is talking on TeamSpeak
            if (idx == -1                                       //Noone on iRacing is talking. They take priority
            &&  Server.getArg("teamspeak-transmitting", true)   //we are allowed to check for teamspeak transmitting
            &&  !teamspeakName.isEmpty()                        //someone is transmitting on teamspeak right now
            ) {
                //now see if this name has a mapping
                String nameMapped = Server.getArg(teamspeakName,teamspeakName);

                if (com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache.SessionDataCars._isMatching(this, teamspeakName, nameMapped)) {
                    d.setValue("TEAMSPEAK");
                    d.setState(Data.State.NORMAL);
                    return d;
                }
                else {
                    String driverName = this.getDriverName().getString();
                    nameMapped = Server.getArg(driverName,driverName);
                    if (com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache.SessionDataCars._isMatching(this, teamspeakName, nameMapped)) {
                        d.setValue("TEAMSPEAK");
                        d.setState(Data.State.NORMAL);
                        return d;
                    }
                }
            }
            
            if (!selectedRadio.isEmpty()) {
                Data channel = getRadioChannel();
                String name = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios",Integer.toString(m_lastKnownRadio),"Frequencies",channel.getString(),"FrequencyName");
                d.setValue(name == null ? "" : (name.startsWith("@") ? name.substring(1) : name));  //is null in a replay and strip the @
                d.setState(Data.State.NORMAL);
            }
            else {
                d.setValue("ALLTEAMS");
                d.setState(Data.State.NOTAVAILABLE);
            }
        }
        return d;
    }

    @Override
    public Data getSpotterMessage() {
        Data d = super.getSpotterMessage();
        if (isME()) {
            d.setValue(SpotterMessages.OFF,"",Data.State.NORMAL);
            
            int message = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("CarLeftRight");
            
            if (message == CarLeftRight.LRClear)
                d.setValue(SpotterMessages.CLEAR);
            else
            if (message == CarLeftRight.LRCarRight)
                d.setValue(SpotterMessages.CARRIGHT);
            else
            if (message == CarLeftRight.LRCarLeft)
                d.setValue(SpotterMessages.CARLEFT);
            else
            if (message == CarLeftRight.LRCarLeftRight)
                d.setValue(SpotterMessages.CARLEFTRIGHT);
            else
            if (message == CarLeftRight.LR2CarsRight)
                d.setValue(SpotterMessages.CARSRIGHT);
            else
            if (message == CarLeftRight.LR2CarsLeft)
                d.setValue(SpotterMessages.CARSLEFT);
        }
        return d;
    }
    
    @Override
    public Data getStartFinishTimes() {
        Data d = super.getStartFinishTimes();
        d.setValue(m_timeAtStartFinish,"s",Data.State.NORMAL);
        return d;
    }
    
    @Override
    public Data getStatus() {
        Data d = super.getStatus();
        d.setValue(m_prevStatus.getState(),"State",Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getWarnings() {
        Data d = super.getWarnings();
        if (isME()) {
            d.setState(Data.State.OFF);
            
            int warnings     = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("EngineWarnings");

            StringBuffer s = new StringBuffer(String.format("%d;0x%X;",warnings,warnings));
            if ((warnings & EngineWarnings.waterTempWarning) != 0)              s.append("WATERTEMPWARNING;") ;
            if ((warnings & EngineWarnings.fuelPressureWarning) != 0)           s.append("FUELPRESSUREWARNING;") ;
            if ((warnings & EngineWarnings.oilPressureWarning) != 0)            s.append("OILPRESSUREWARNING;") ;
            if ((warnings & EngineWarnings.engineStalled) != 0)                 s.append("ENGINESTALLED;") ;
            if ((warnings & EngineWarnings.pitSpeedLimiter) != 0 
            &&  !m_iRacingSIMPlugin.getSession().getIsReplay().getBoolean())    s.append("PITSPEEDLIMITER;") ;   //don't turn this on while in a replay
            if ((warnings & EngineWarnings.revLimiterActive) != 0)              s.append("REVLIMITER;") ;
            if ((m_sessionFlags & SessionFlags.repair) != 0)                    s.append("REPAIRSREQUIRED;");
            /*
                The following are not provided by iRacing, so we will derive them
                and in some cases fine tune them by car.
            */

        //WATER
            Data WaterTemp = _getGauge(Gauge.Type.WATERTEMP).getValueCurrent(); //new Data("WaterTemp",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("WaterTemp"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("WaterTemp").Unit);

            if (WaterTemp.getState().equals(Data.State.CRITICAL))
                s.append("WATERTEMPCRITICAL;");
            else
            if (WaterTemp.getState().equals(Data.State.WARNING))
                s.append("WATERTEMPWARNING;");

            Data WaterPressure = _getGauge(Gauge.Type.WATERPRESSURE).getValueCurrent();

            if (WaterPressure.getState().equals(Data.State.CRITICAL))
                s.append("WATERPRESSURECRITICAL;");
            else
            if (WaterPressure.getState().equals(Data.State.WARNING))
                s.append("WATERPRESSUREWARNING;");

            Data WaterLevel = _getGauge(Gauge.Type.WATERLEVEL).getValueCurrent(); //new Data("WaterLevel",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("WaterLevel"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("WaterLevel").Unit);

            if (WaterLevel.getState().equals(Data.State.CRITICAL))
                s.append("WATERLEVELCRITICAL;");
            else
            if (WaterLevel.getState().equals(Data.State.WARNING))
                s.append("WATERLEVELWARNING;");

        //OIL
            Data OilTemp = _getGauge(Gauge.Type.OILTEMP).getValueCurrent(); //new Data("OilTemp",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("OilTemp"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("OilTemp").Unit);

            if (OilTemp.getState().equals(Data.State.CRITICAL))
                s.append("OILTEMPCRITICAL;");
            else
            if (OilTemp.getState().equals(Data.State.WARNING))
                s.append("OILTEMPWARNING;");

            Data OilPressure = _getGauge(Gauge.Type.OILPRESSURE).getValueCurrent(); //new Data("OilPress",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("OilPress"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("OilPress").Unit);

            if (OilPressure.getState().equals(Data.State.CRITICAL))
                s.append("OILPRESSURECRITICAL;");
            else
            if (OilPressure.getState().equals(Data.State.WARNING))
                s.append("OILPRESSUREWARNING;");

            Data OilLevel = _getGauge(Gauge.Type.OILLEVEL).getValueCurrent(); //new Data("OilLevel",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("OilLevel"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("OilLevel").Unit);

            if (OilLevel.getState().equals(Data.State.CRITICAL))
                s.append("OILLEVELCRITICAL;");
            else
            if (OilLevel.getState().equals(Data.State.WARNING))
                s.append("OILLEVELWARNING;");

        //FUEL
            Data FuelPressure = _getGauge(Gauge.Type.FUELPRESSURE).getValueCurrent(); //new Data("FuelPress",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("FuelPress"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelPress").Unit);

            if (FuelPressure.getState().equals(Data.State.CRITICAL))
                s.append("FUELPRESSURECRITICAL;");
            else
            if (FuelPressure.getState().equals(Data.State.WARNING))
                s.append("FUELPRESSUREWARNING;");

            Data FuelLevel = _getGauge(Gauge.Type.FUELLEVEL).getValueCurrent(); //new Data("FuelLevel",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("FuelLevel"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);

            if (FuelLevel.getState().equals(Data.State.CRITICAL))
                s.append("FUELLEVELCRITICAL;");
            else
            if (FuelLevel.getState().equals(Data.State.WARNING))
                s.append("FUELLEVELWARNING;");

        //VOLTAGE
            Data Voltage = _getGauge(Gauge.Type.VOLTAGE).getValueCurrent(); //new Data("Voltage",m_iRacingSIMPlugin.getIODriver().getVars().getDouble("Voltage"),m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("Voltage").Unit);

            if (Voltage.getState().equals(Data.State.CRITICAL))
                s.append("VOLTAGECRITICAL;");
            else
            if (Voltage.getState().equals(Data.State.WARNING))
                s.append("VOLTAGEWARNING;");
            d.setValue(s.toString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    public Data getDivisionName() {
        Data d = new Data("CarDivisionName","");
        d.setState(Data.State.OFF);

        if (isValid()) {
            d.setValue( m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"DivisionName") );
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    public Data getDriverLicLevel() {
        Data d = new Data("CarDriverLicLevel",0);
        d.setState(Data.State.OFF);

        try {
            if (isValid()) {
                d.setValue( Integer.parseInt(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicLevel")) );
                d.setState(Data.State.NORMAL);
            }
        } catch (NumberFormatException e) {}
        
        return d;
    }

    public Data getDriverLicSubLevel() {
        Data d = new Data("CarDriverLicLevel",0);
        d.setState(Data.State.OFF);

        try {
            if (isValid()) {
                d.setValue( Integer.parseInt(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicSubLevel")) );
                d.setState(Data.State.NORMAL);
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public Data getFuelLevelAtStartFinish(String UOM) {
        Data d = super.getFuelLevelAtStartFinish(UOM);
        
        if (isME()) {
            d.setState(Data.State.OFF);
            if (isValid()) {
                d.setValue(m_fuelAtStartFinish,m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);
                d.setState(Data.State.NORMAL);
            }
    
            d.addConversion(this._getGauge(Gauge.Type.FUELLEVEL).getValueCurrent());
        }
        
        return d.convertUOM(_getGauge(Gauge.Type.FUELLEVEL).getUOM().getString()).convertUOM(UOM);
    }

    @Override
    public Data getRepairTime() {
        Data d = super.getRepairTime();
        if (isME()) {
            d.setValue(m_repairTime);
            d.setUOM(m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitRepairLeft").Unit);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getRepairTimeOptional() {
        Data d = super.getRepairTimeOptional();
        if (isME()) {
            d.setValue(m_repairTimeOptional);
            d.setUOM(m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitOptRepairLeft").Unit);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getTeamName() {
        Data d = super.getTeamName();
        d.setState(Data.State.OFF);

        String teams = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TeamRacing");
        if (!teams.isEmpty() && Integer.parseInt(teams) != 0) {
            d.setValue( m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"TeamName") );
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    public boolean isPaceCar() {
        String username = getDriverName().getString();
        //In the Dec 2015 build, a flag was added to identify the pace car
        String isPaceCar = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarIsPaceCar");
        if (!isPaceCar.isEmpty()) {
            if (isPaceCar.equals("1"))
                return true;
            return false;
        }
        if (username.equalsIgnoreCase("Pace Car") || username.equalsIgnoreCase("Safety Car"))
            return true;
        return false;
    }
    
    public boolean isValid() {
        if (!m_iRacingSIMPlugin.getIODriver().isConnected())
            return false;

        if (m_name.equals("PITSTALL"))
            return true;

        if (super.isValid())
            return m_driversIdx != -1;
        return false;
    }

    //This version of isValid() is called by iRacingSIMPlugin._getNewData() to see if the car class needs to be reloaded.
    public boolean isValid(int id,int driversIdx) {
        if (super.isValid() && m_iRacingSIMPlugin.getIODriver().isConnected()) {
            if( m_id == id ) {
                if (m_driversIdx != -1) {
                    if (m_driversIdx == driversIdx) {
                        if (m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumber").equals(m_number)
                        &&  m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarPath").equals(m_name)
                        //&&  m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserName").equals(m_driverName)
                        ) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public    Data setAdminFlag(boolean onOffFlag) {
        Data d = super.setAdminFlag(onOffFlag);
        d.setState(Data.State.OFF);
        
        if (!m_number.isEmpty()) {
            if (onOffFlag) {
                d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin
                    .getSession().getSendKeys("ADMIN_COMMANDS", "ADMIN")
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[DRIVER]", m_number)
                ).getString());
            }
            else {
                d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                    .getSendKeys("ADMIN_COMMANDS", "NADMIN")
                    .replace("[DRIVER]", m_number)
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                ).getString());
            }
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data setBlackFlag(int quantity,String uom) {
        Data d = super.setBlackFlag(quantity,uom);
        d.setState(Data.State.OFF);

        if (!m_number.isEmpty()) {
            if (uom.equalsIgnoreCase("lap"))
                d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin
                    .getSession().getSendKeys("ADMIN_COMMANDS", "BLACK")
                    .replace("[DRIVER]", m_number)
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[TIME]", String.format("L%d", quantity))
                ).getString());
            else {
                if (quantity < 0)
                    d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin
                        .getSession().getSendKeys("ADMIN_COMMANDS", "BLACK")
                        .replace("[DRIVER]", m_number)
                        .replace("[NUMBER]", m_number)
                        .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                        .replace("[TIME]", "D")
                    ).getString());  //drive through
                else
                    d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                        .getSendKeys("ADMIN_COMMANDS", "BLACK")
                        .replace("[DRIVER]", m_number)
                        .replace("[NUMBER]", m_number)
                        .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                        .replace("[TIME]", String.format("%d", quantity))
                    ).getString());
            }
    
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public    Data setChat(String text) {
        Data d = super.setChat(text);
        d.setValue(m_iRacingSIMPlugin.getSession().setChat(
            this.m_iRacingSIMPlugin.getSession().getSendKeys("CHAT", "DRIVER")
                .replace("[DRIVER]", m_number)
                .replace("[NUMBER]", m_number)
                .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                .replace("[TEXT]",text)
        ).getString());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public    Data setChatFlag(boolean onOffFlag) {
        Data d = super.setChatFlag(onOffFlag);
        d.setState(Data.State.OFF);
        
        if (!m_number.isEmpty()) {
            if (onOffFlag) {
                d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                    .getSendKeys("ADMIN_COMMANDS", "CHAT")
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVER]", m_number)
                ).getString());
            }
            else {
                d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                        .getSendKeys("ADMIN_COMMANDS", "NCHAT")
                        .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                        .replace("[NUMBER]", m_number)
                        .replace("[DRIVER]", m_number)
                    ).getString());
            }
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setClearPenaltiesFlag() {
        Data d = super.setClearPenaltiesFlag();
        d.setState(Data.State.OFF);
        
        if (!m_number.isEmpty()) {
            d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                    .getSendKeys("ADMIN_COMMANDS", "CLEAR")
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVER]", m_number)
                ).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setDisqualifyFlag() {
        Data d = super.setDisqualifyFlag();
        d.setState(Data.State.OFF);
        
        if (!m_number.isEmpty()) {
            d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                    .getSendKeys("ADMIN_COMMANDS", "DQ")
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVER]", m_number)
                ).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setEndOfLineFlag() {
        Data d = super.setEndOfLineFlag();
        d.setState(Data.State.OFF);
        
        if (!m_number.isEmpty()) {
            d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                    .getSendKeys("ADMIN_COMMANDS", "EOL")
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVER]", m_number)
                ).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override    
    public    Data setReloadPaint() {
        Data d = super.setReloadPaint();

        if (isValid()) {
            m_iRacingSIMPlugin.getIODriver().broadcastMsg(BroadcastMsg.BroadcastReloadTextures,ReloadTexturesMode.ReloadTextures_CarIdx,this.m_id);
            d.setValue(String.format("Reloading Texture (i.e. Paint) for #%s", m_number));
            d.setState(Data.State.NORMAL);
            Server.logger().info(d.getString());
        }
        return d;
    }
    
    @Override
    public    Data setRemoveFlag() {
        Data d = super.setRemoveFlag();
        d.setState(Data.State.OFF);
        
        if (!m_number.isEmpty()) {
            d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                    .getSendKeys("ADMIN_COMMANDS", "REMOVE")
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVER]", m_number)
                ).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setWaveAroundFlag() {
        Data d = super.setWaveAroundFlag();
        d.setState(Data.State.OFF);
        
        if (!m_number.isEmpty()) {
            d.setValue(m_iRacingSIMPlugin.getSession().setChat(this.m_iRacingSIMPlugin.getSession()
                    .getSendKeys("ADMIN_COMMANDS", "WAVEBY")
                    .replace("[DRIVERNAME]", getDriverName(false).getString().replace(" ","."))
                    .replace("[NUMBER]", m_number)
                    .replace("[DRIVER]", m_number)
                ).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    private void _resetDetected() {
        if (isME()) {
            ((iRacingGauge)_getGauge(Gauge.Type.TIREPRESSURERF))._resetDetected();
            ((iRacingGauge)_getGauge(Gauge.Type.TIREPRESSURERR))._resetDetected();
            ((iRacingGauge)_getGauge(Gauge.Type.TIREPRESSURELF))._resetDetected();
            ((iRacingGauge)_getGauge(Gauge.Type.TIREPRESSURELR))._resetDetected();
            ((iRacingGauge)_getGauge(Gauge.Type.FUELLEVEL))._resetDetected();
            ((iRacingGauge)_getGauge(Gauge.Type.WINDSHIELDTEAROFF))._resetDetected();
            ((iRacingGauge)_getGauge(Gauge.Type.FASTREPAIRS))._resetDetected();
            
//            //Loop through the gauges and if a Changeables class, reset it
//            Iterator<Entry<String, Gauge>> itr = m_gauges.entrySet().iterator();
//            while (itr.hasNext()) {
//                Entry<String, Gauge> gaugeEntry = itr.next();
//                Gauge gauge = gaugeEntry.getValue();
//                if (gauge instanceof Changeables) {
//                    ((iRacingGauge)gauge)._resetDetected();
//                }
//            }
        }
    }
    
    //This gets called every tick from the iRacingSIMPlugin loop.
    //Be careful not to put too much in here that will slow it down
    //read any values from the Session String in _initiallize() unless you think they will change. Then I would defer that read to the function that needs it.
    public boolean onDataVersionChange() {

        //double prevSessionTime         = m_sessionTime;
        double prevLapCompletedPercent = m_lapCompletedPercent;
        //int    prevLapCompleted        = m_lapCompleted;
        double lapCompletedPercent     = m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLapDistPct") != null 
                                       ? m_iRacingSIMPlugin.getIODriver().getVars().getDouble("CarIdxLapDistPct", m_id)
                                       : (isME() ? m_iRacingSIMPlugin.getIODriver().getVars().getDouble("LapDistPct") : -1.0);
        double prevFuelLevel           = m_fuelLevel;
        int    prevSessionFlags        = m_sessionFlags;
        boolean isReset                = false;
//        boolean isDriving              = isME() && m_iRacingSIMPlugin.getIODriver().getVars().getBoolean("IsOnTrack");   //This should be set when you are in the car and isME() is true.
        
        m_sessionFlags= m_iRacingSIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
        if (m_sessionFlags == -1)
            m_sessionFlags = 0;
        
        if (!isME()) {
            //only use the ones that are not specific to me
            m_sessionFlags = m_sessionFlags & (
                SessionFlags.checkered      |
                SessionFlags.white          |
                SessionFlags.green          |
//                SessionFlags.yellow         |
                SessionFlags.red            |
                SessionFlags.debris         |
                SessionFlags.crossed        |
//                SessionFlags.yellowWaving   |
                SessionFlags.oneLapToGreen  |
//                SessionFlags.greenHeld      |
                SessionFlags.tenToGo        |
                SessionFlags.fiveToGo       |
                SessionFlags.caution        |
                SessionFlags.cautionWaving  |
                SessionFlags.startHidden    |
                SessionFlags.startReady     |
                SessionFlags.startSet       |
                SessionFlags.startGo        
            );
        }
        
        m_sessionTime = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("SessionTime");

//moved this to _initialize()
//        int sessionNum = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("SessionNum");
//        //cache the session type so we aren't parsing in the Session String during these updates every time.
//        if (m_sessionTypes.containsKey(sessionNum)) {
//        	m_sessionType = m_sessionTypes.get(sessionNum);
//        }
//        else {
//        	m_sessionType = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(sessionNum),"SessionType").toUpperCase();
//        	m_sessionTypes.put(sessionNum,m_sessionType);
//        }

        State surfacelocation = new State("",0.0);

        //nextStatus will hold the status for this tick, then at the end of this procedure, copy it to m_prevStatus
        //so, m_prevStatus.getState() and m_prevStatus.getPrevState() can be used to look back to make decisions.
        State nextStatus = new State(Car.Status.INVALID,m_sessionTime);

        //There are some values needed when car is the PITSTALL
        if (this.m_name.equals("PITSTALL")) {
            //tried to move to _initialize(), but not populated on race start. So check for it here and cache it so we don't read it every tick.
//            if (m_lapCompletedPercent < 0.0) {
//                String s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverPitTrkPct");
//                if (!s.isEmpty())
//                    this.m_lapCompletedPercent = Double.parseDouble(s);
//            }
            Data pitLocation      = m_iRacingSIMPlugin.getSession().getCar(Session.CarIdentifiers.REFERENCE).getPitLocation();
            //only if we know the pit location of the reference car, update these variables on the pitstall car.
            if (pitLocation.getState().equals(Data.State.NORMAL))
                m_pitLocation         = pitLocation.getDouble() / 100.0;
            else 
                m_pitLocation         = -1.0;
            
            m_lapCompletedPercent = m_pitLocation;
            m_prevStatus.setState(Car.Status.INPITSTALL, m_sessionTime);
            return true;
        }

        if (m_id == -1) {
            m_prevStatus.setState(nextStatus);
            return false;
        }

//moved to getTeamName()        
//        String teams = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TeamRacing");
//        if (!teams.isEmpty() && Integer.parseInt(teams) != 0) {
//        }

        //In team events, this can change, but the caridx and car number will stay the same
        //So, keep it updated in real-time.
        //m_driverName = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserName");
        
//      "RadioTransmitCarIdx": -1,
//      "RadioTransmitFrequencyIdx": 5,
//      "RadioTransmitRadioIdx": 0,
        if (!isME()) {
            String s = m_iRacingSIMPlugin.getIODriver().getVars().getString("RadioTransmitRadioIdx");
            if (!s.isEmpty()) {
                m_lastKnownRadio = Integer.parseInt(s);
                s = m_iRacingSIMPlugin.getIODriver().getVars().getString("RadioTransmitCarIdx");
                if (!s.isEmpty() && Integer.parseInt(s) == m_id) {
                    s = m_iRacingSIMPlugin.getIODriver().getVars().getString("RadioTransmitFrequencyIdx");
                    if (!s.isEmpty())
                        m_lastKnownFrequency = Integer.parseInt(s);
                }
            }
        }
        

        //iRacing has a bug where it outputs lap 2 at the start of the race while the green flag is out
        //for about the first 10% of the race. It is really lap one
        int    currentLap              = (isValid() ? m_iRacingSIMPlugin.getIODriver().getVars().getInteger("CarIdxLap", m_id) : -1);
        
        //if we can't get a currentLap from the array, fall back to the none array for IBT files.
        if (currentLap == -1 && isME() && m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLap") == null)
            currentLap = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("Lap");
        
        if (currentLap == 2 && (m_sessionFlags & SessionFlags.green) != 0)
            currentLap = 1;
        
        //track the incidents by lap. This uses the var version for ME, else rely on session for others.
        if (currentLap > 0) {
            int myIncidents = isME()
                            ? m_iRacingSIMPlugin.getIODriver().getVars().getInteger("PlayerCarMyIncidentCount")
                            : m_iRacingSIMPlugin.getIODriver().getSessionInfo().getInteger("DriverInfo","Drivers",m_driversIdx.toString(),"CurDriverIncidentCount");
                            
            //first build up the array
            while (m_myIncidentsLap.size() < currentLap) {
                m_myIncidentsLap.add(0);
            }
            if (myIncidents > 0) {
                //if it has changed, add it to the current lap
                if (myIncidents > m_myIncidents)
                    m_myIncidentsLap.set(currentLap-1,m_myIncidentsLap.get(currentLap-1) + (myIncidents - m_myIncidents));
                m_myIncidents = myIncidents;
            }
            
            //now do the driver
            int driverIncidents = isME()
                                ? m_iRacingSIMPlugin.getIODriver().getVars().getInteger("PlayerCarDriverIncidentCount")
                                : m_iRacingSIMPlugin.getIODriver().getSessionInfo().getInteger("DriverInfo","Drivers",m_driversIdx.toString(),"CurDriverIncidentCount");
                                
            //first build up the array
            while (m_driverIncidentsLap.size() < currentLap) {
                m_driverIncidentsLap.add(0);
            }
            if (driverIncidents > 0) {
                //if it has changed, add it to the current lap
                if (driverIncidents > m_driverIncidents)
                    m_driverIncidentsLap.set(currentLap-1,m_driverIncidentsLap.get(currentLap-1) + (driverIncidents - m_driverIncidents));
                m_driverIncidents = driverIncidents;
                if (m_id != m_ME)
                    ((iRacingSession)m_iRacingSIMPlugin.getSession())._setHasIncidents(m_driverIncidents);
            }
            
            //now do the team
            int teamIncidents = isME()
                              ? m_iRacingSIMPlugin.getIODriver().getVars().getInteger("PlayerCarTeamIncidentCount")
                              : m_iRacingSIMPlugin.getIODriver().getSessionInfo().getInteger("DriverInfo","Drivers",m_driversIdx.toString(),"TeamIncidentCount");
                              
            //first build up the array
            while (m_teamIncidentsLap.size() < currentLap) {
                m_teamIncidentsLap.add(0);
            }
            if (teamIncidents > 0) {
                //if it has changed, add it to the current lap
                if (teamIncidents > m_teamIncidents)
                    m_teamIncidentsLap.set(currentLap-1,m_teamIncidentsLap.get(currentLap-1) + (teamIncidents - m_teamIncidents));
                m_teamIncidents = teamIncidents;
            }
        }        
        
        if (isME()) {
            double fuelLevel = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("FuelLevel");
            if (fuelLevel >= 0.0)
                m_fuelLevel = fuelLevel;
            if (m_iRacingSIMPlugin.getIODriver().getSessionInfo().isDataParsed()) {
                String s = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverPitTrkPct");
                if (!s.isEmpty()) {
                    m_pitLocation = Double.parseDouble(s);
                    if (m_pitLocation < 0.0 || m_pitLocation > m_iRacingSIMPlugin.getSession().getTrack()._maxPercentage())
                        m_pitLocation = 0.0;
                }
            }
            //see below for setting the pit location for other cars when they eventually stop in their pit stall. 
                
            //if the fuel level increases and it wasn't checked, then we must have 
            //either reset or clicked new car
            //apparently iRacing resets the flag as it starts fueling.
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitSvFlags") != null
            &&  prevFuelLevel > -1.0
            &&  m_fuelLevel > (prevFuelLevel + Server.getArg("fuel-level-reset-minimum-liters", 1.0)) //greater than you can fill since last tick. Using 1 liter for now.
            && ((m_iRacingSIMPlugin.getIODriver().getVars().getBitfield("PitSvFlags") & PitSvFlags.FuelFill) == 0)
            ) {
                m_isNewCar = true;
                Server.logger().fine(String.format("#%-3s (id=%-2d) New Car because fuel increased on Lap(%-3.3f) from (%-3.3f) to (%-3.3f), DataVersion=(%s)",
                        m_number,m_id,
                        (double)currentLap + this.m_lapCompletedPercent,
                        prevFuelLevel,
                        m_fuelLevel,
                        m_iRacingSIMPlugin.getSession().getDataVersion().getString() //getIODriver().getHeader().getLatest_VarBufTick()
                    ));
            }
        }

        int trackSurface = TrackSurface.getTrackSurface(m_iRacingSIMPlugin.getIODriver(),m_id,isME());
        surfacelocation.setState(trackSurface,m_sessionTime);
        
        if (isME() && m_iRacingSIMPlugin.getIODriver().getVars().getBoolean("IsInGarage") /*&& m_lapCompletedPercent == -1.0*/) {
            nextStatus.setState(iRacingCar.Status.INGARAGE,m_sessionTime);
        }
        else
        if (isME() && m_iRacingSIMPlugin.getIODriver().getVars().getDouble("PlayerCarTowTime") > 0.0) {  //June 2018?
            nextStatus.setState(iRacingCar.Status.TOWING,m_sessionTime);
        }
        else
        if (surfacelocation.equals(TrackSurface.InPitStall)) {
            nextStatus.setState(iRacingCar.Status.INPITSTALL,m_sessionTime);
            m_timeBeforeNextStateChange = m_sessionTime + INVALID_INPITSTALL;
        }
        else
//20160131181057.909: FINE   : #7   (id=7 ) surfacelocation changed on Lap(22.091) from (ApproachingPits  ) to (InPitStall       ), DataVersion=(169-84536): com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar.onDataVersionChange(iRacingCar.java:2008)[Servlet.DataService.iRacing]
//20160131181112.635: FINE   : #7   (id=7 ) surfacelocation changed on Lap(22.093) from (InPitStall       ) to (ApproachingPits  ), DataVersion=(172-85420): com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar.onDataVersionChange(iRacingCar.java:2008)[Servlet.DataService.iRacing]
//20160131181112.680: FINE   : #7   (id=7 ) surfacelocation changed on Lap(22.093) from (ApproachingPits  ) to (InPitStall       ), DataVersion=(172-85423): com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar.onDataVersionChange(iRacingCar.java:2008)[Servlet.DataService.iRacing]
//20160131181112.746: FINE   : #7   (id=7 ) surfacelocation changed on Lap(22.093) from (InPitStall       ) to (ApproachingPits  ), DataVersion=(172-85427): com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar.onDataVersionChange(iRacingCar.java:2008)[Servlet.DataService.iRacing]
//20160131181126.393: FINE   : #7   (id=7 ) surfacelocation changed on Lap(22.284) from (ApproachingPits  ) to (OnTrack          ), DataVersion=(172-86245): com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar.onDataVersionChange(iRacingCar.java:2008)[Servlet.DataService.iRacing]
//due to this log, this was the pole setter. When leaving his pit he would bypass on pitroad
//and go directly to approaching pits. He must have stopped right on the edge.
//So, if they were in the stall, wait a little to see if they really left.            
//this delay can be tricky if they overshoot their pit and backup. 
//So, it should be small, but large enough to get rid of the false reads.
        if (m_prevStatus.equals(iRacingCar.Status.INPITSTALL)
        &&  m_timeBeforeNextStateChange > m_sessionTime
        ) {
            nextStatus.setState(iRacingCar.Status.INPITSTALL,m_sessionTime);
        }
        else
        if (surfacelocation.equals(TrackSurface.OnPitRoad)) {
            nextStatus.setState(iRacingCar.Status.ONPITROAD,m_sessionTime);
        }
        else
        if (surfacelocation.equals(TrackSurface.AproachingPits)) {
            nextStatus.setState(iRacingCar.Status.APPROACHINGPITS,m_sessionTime);
        }
        else
        if (surfacelocation.equals(TrackSurface.OnTrack)) {
            nextStatus.setState(iRacingCar.Status.ONTRACK,m_sessionTime);
        }
        else
        if (surfacelocation.equals(TrackSurface.OffTrack)) {
            nextStatus.setState(iRacingCar.Status.OFFTRACK,m_sessionTime);
        }

        if (!m_surfacelocation.equals(surfacelocation)) {
            if (Server.logger().getLevel().intValue() <= Level.FINE.intValue())
            Server.logger().fine(String.format("#%-3s (id=%-2d) surfacelocation changed on Lap(%-3.3f) from (%-17s) to (%-17s), DataVersion=(%s)",
                m_number,m_id,
                (double)currentLap + this.m_lapCompletedPercent,
                TrackSurface.toString(m_surfacelocation.getState()),
                TrackSurface.toString(surfacelocation.getState()),
                m_iRacingSIMPlugin.getSession().getDataVersion().getString() //getIODriver().getHeader().getLatest_VarBufTick()
            ));
            m_surfacelocation.setState(surfacelocation);
        }
        
//The next few statements are trying to add more states than iRacing gives ME
//Specifically, ENTERINGPITSTALL, EXITINGPITSTALL, LEAVINGPITROAD
//if (isME() && !nextStatus.equals(iRacingCar.Status.INVALID))
//    currentLap = currentLap;

        //if iRacing says we're in the pit stall, derive if we're entering and haven't stopped yet.
        if (nextStatus.equals(iRacingCar.Status.INPITSTALL)) {

            //if they were already entering, have they stopped in the pit stall
            if (m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)) {
                
                //The lap completed percentage is not reliable since each tick may not change it
                //especially for other cars because of netcode delays.
                //So we will do it anyway and also use the brake for the driver as iRacing sets it to 100% in the pits.
                double stoppedFactor = Server.getArg("pit-stopped-factor",100000.0);
                double brake = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("Brake");
                boolean stopped = isME()
                                ? ((m_prevStatus.getTime(iRacingCar.Status.ENTERINGPITSTALL,m_sessionTime) < ENTER_PIT_DELAY)
                                   &&
                                   (Math.round(prevLapCompletedPercent * stoppedFactor) == Math.round(lapCompletedPercent * stoppedFactor))
//This was found not very reliable due to it didn't set the brake if not servicing
//                                   && 
//                                   brake == 1.0
                                  )
                                : ((m_prevStatus.getTime(iRacingCar.Status.ENTERINGPITSTALL,m_sessionTime) < ENTER_PIT_DELAY)
                                    &&
                                   (Math.round(prevLapCompletedPercent * stoppedFactor) == Math.round(lapCompletedPercent * stoppedFactor))
                                  );
                        
                //don't let it change to INPITSALL until we know they are stopped. isME().speed can be used, but other cars?
                if (!stopped
//                &&  !m_prevStatus.equalsPrevious(iRacingCar.Status.INVALID)
                ) {
                    //keep them in the Entering state until they are stopped.
                    nextStatus.setState(iRacingCar.Status.ENTERINGPITSTALL,m_sessionTime);
                }
                else {
                    Server.logger().fine(String.format("#%-3s (id=%-2d) InPitStall on Lap(%-3.3f), PrevPct=(%.15f), Pct=(%.15f), Brake=(%f), DataVersion=(%s)",
                            m_number,m_id,
                            (double)currentLap + this.m_lapCompletedPercent,
                            prevLapCompletedPercent,lapCompletedPercent,brake,
                            m_iRacingSIMPlugin.getSession().getDataVersion().getString() //getIODriver().getHeader().getLatest_VarBufTick()
                    ));
                    
                    //add the delay back in
                    //tweaked a little to compensate for entry and exit time
                    nextStatus = new State(iRacingCar.Status.INPITSTALL,m_sessionTime);
                }
            }

            //if we just entered the Pit Stall, and we didn't just exit (over shot and backed up)
            if (!m_prevStatus.equals(iRacingCar.Status.INPITSTALL)
            &&  !m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)
            &&  !m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
            ) {
                nextStatus.setState(iRacingCar.Status.ENTERINGPITSTALL,m_sessionTime);
            }
        }
        else { //else iRacing Says we're not in the Pit Stall, derive if we're Exiting

            //if they have not cleared the pits long enough to make sure they didn't overshoot and backed up, then stay in exiting pits mode
            if (m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
            &&  m_prevStatus.getTime(m_sessionTime) < EXIT_PIT_DELAY
            ) {
                nextStatus.setState(iRacingCar.Status.EXITINGPITSTALL,m_prevStatus.getStartingTime());
            }

            //if they get out of the car and click "new car", then the current lap returns -1 until they enter the track
            if (isME()
            &&  currentLap == -1
            &&  m_lapCompleted > 0
            &&  (m_prevStatus.equals(iRacingCar.Status.INPITSTALL) || m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL))
            &&  nextStatus.equals(iRacingCar.Status.INVALID)
            ) {
                nextStatus.setState(iRacingCar.Status.INGARAGE,m_sessionTime);  //TODO: Need OUTOFCAR status
                m_isNewCar = true;
            }
//            else
//            //if we were in the pit stall and we blinked, 
//            //make it wait so we don't loose pit time if they're just blinking
//            if (m_prevStatus.equals(iRacingCar.Status.INPITSTALL)
//            &&  nextStatus.equals(iRacingCar.Status.INVALID)
//            &&  m_prevStatus.getTimeSinceUpdate(m_sessionTime) < 2.0 //after 2 seconds, we will allow invalid
//            ) {
//                nextStatus.setState(iRacingCar.Status.INPITSTALL,m_sessionTime);
//            }
            else
            //if we just left the pit stall, set the state to Exiting
            if (m_prevStatus.equals(iRacingCar.Status.INPITSTALL)
            ) {
                nextStatus.setState(iRacingCar.Status.EXITINGPITSTALL,m_sessionTime);
            }
        }

//if (isME()) {
//    if (m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
//    &&  (nextStatus.equals(iRacingCar.Status.ONTRACK) || nextStatus.equals(iRacingCar.Status.OFFTRACK))
//    ) {
//        if (this.m_lapCompletedPercent > m_mergePoint)
//            m_mergePoint=m_mergePoint;
//    }
//}
        m_mergePoint = m_iRacingSIMPlugin.getSession().getTrack()._getMergePoint(m_mergePointReference * 100.0) / 100.0;

        if (m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
        &&  (nextStatus.equals(iRacingCar.Status.ONTRACK) || nextStatus.equals(iRacingCar.Status.OFFTRACK))
        &&  m_mergePoint > 0.0
        &&  this.m_lapCompletedPercent <= m_mergePoint
        ) {
            nextStatus.setState(iRacingCar.Status.LEAVINGPITS,m_sessionTime);
        }
        
        //if the previous state was on pit road or leaving pits
        //then set it to leaving pits
        if (nextStatus.equals(iRacingCar.Status.APPROACHINGPITS)
        && (  m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
           || m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
           || m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
           )
        ) {
            nextStatus.setState(iRacingCar.Status.LEAVINGPITS,m_sessionTime);
            m_mergePointReference = m_lapCompletedPercent; //save our position of where we left the pits as the reference for determining the merge point
            //so if we just left the pits and we haven't reached the merge point
            //yet iRacing says we're on the track, set the next status back to leaving pits.
            //This will help the track map logic keep the car on the apron or access road longer.
            //The merge point reference is our position when we left pit road. Tracks with multiple pit roads will have multiple merge points.

        }

        //if the if the user is reseting pit box, then we have to wait until they are done before we can
        //send our pit commands or they won't stick
        if (m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox() == 1
        &&  nextStatus.equals(iRacingCar.Status.LEAVINGPITS)
        &&  !m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
        &&  m_prevStatus.getTime(m_sessionTime) < RESET_PIT_DELAY
        ) {
            nextStatus.setState(iRacingCar.Status.ONPITROAD,m_prevStatus.getStartingTime());
        }

//End of derived status

        if (isME() && !m_prevStatus.equals(nextStatus)) {
            Server.logger().info(String.format("#%-3s (id=%-2d) Status changed on Lap(%-3.3f) from (%-17s) to (%-17s), DataVersion=(%s)",
                m_number,m_id,
                (double)currentLap + this.m_lapCompletedPercent,
                m_prevStatus.getState(),
                nextStatus.getState(),
                m_iRacingSIMPlugin.getSession().getDataVersion().getString() //getIODriver().getHeader().getLatest_VarBufTick()
            ));
        }

        //exit if we can't get a reading of where we are on the current lap
        if (lapCompletedPercent == -1.0 /*&& nextStatus.equals(Car.Status.INVALID)*/) {
            m_prevStatus.setState(nextStatus);
            return false;
        }

        //so count it as a blink we were invalid before we came back on the track
        if (  m_prevStatus.equals(iRacingCar.Status.INVALID)
        && (  nextStatus.equals(iRacingCar.Status.APPROACHINGPITS)
           || nextStatus.equals(iRacingCar.Status.ONPITROAD)
           || nextStatus.equals(iRacingCar.Status.LEAVINGPITS)
           || nextStatus.equals(iRacingCar.Status.OFFTRACK)
           || nextStatus.equals(iRacingCar.Status.ONTRACK)
           || nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)
//it's common to enter the pitstall from invalid, such as when you just came from the garage or a reset
//           || nextStatus.equals(iRacingCar.Status.INPITSTALL)
           || nextStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
           )
        ) {
            m_discontinuality++;
        }

        //exit if we can't get a reading of the current lap
        if (currentLap == -1) {
            
            if (nextStatus.equals(Car.Status.INVALID)) {                 

                //If in the pit stall, wait at least 1 second before allowing the state to change to invalid
                //This will allow the car to have a short blink and not affect the time in the stall
                //This will prevent blinkers from distorting their pit time. 
                //If really leaving, you will stay invalid for more than a second
                if (m_prevStatus.equals(Car.Status.INPITSTALL) 
                &&  m_prevStatus.getTime(Car.Status.INPITSTALL, m_sessionTime) > INVALID_INPITSTALL
                ) {
                    m_prevStatus.setState(nextStatus);
                }
            }
            
            return false;
        }
        
        //in iRacings Mount Washington Track, the percentages returned before the starting line
        //is very high. Just set them to zero for now.
        
        if (lapCompletedPercent > 1.5) 
            lapCompletedPercent = 0.0;

        //If lapCompletedPercent is > 1.0, the current lap doesn't, so we increment it
        //It appears there is a delay of these 2 variables and they are not in sync.
        //Also, the low level driver is not normalizing the pct, so we would have to change that also.
        if (lapCompletedPercent >= m_iRacingSIMPlugin.getSession().getTrack()._maxPercentage()) {
            currentLap++;
            lapCompletedPercent -= m_iRacingSIMPlugin.getSession().getTrack()._maxPercentage();
        }

        //I've seen this be negative, but when it is, the current lap has not incremented
        //so, adjust it to be positive.
        if (lapCompletedPercent < 0.0 && lapCompletedPercent > -m_iRacingSIMPlugin.getSession().getTrack()._maxPercentage())
            lapCompletedPercent += m_iRacingSIMPlugin.getSession().getTrack()._maxPercentage();

//if (isME()) //just to have a place to put a breakpoint
//    m_lapCompletedPercent = lapCompletedPercent;
//else
       m_lapCompletedPercent = lapCompletedPercent;

       //help out our speed reader by sending this data to it every tick
//       m_speedReader.onDataVersionChange(m_sessionTime, m_lapCompletedPercent, m_trackLength.getDouble());
       
//        //did the car just cross the finish line?
//        if (m_lapCompletedPercent   >= 0.0 && m_lapCompletedPercent   < 0.1
//        &&  prevLapCompletedPercent >= 0.9 && prevLapCompletedPercent < 1.0
//        ) {
//            //TODO: interpolate this using prevSessionTime and the percentCompleted to get an better time at the start/finish
//            m_timeAtStartFinish = m_sessionTime < 0.0 ? 0.0 : m_sessionTime;
//
//            if (isME()) {
//                if (m_fuelAtStartFinish > -1.0) {
//                    double fuelUsed = 0.0;
//                    fuelUsed = m_fuelAtStartFinish - m_fuelLevel >= 0.0 ? m_fuelAtStartFinish - m_fuelLevel : 0.0;
//                    while (m_fuelConsummed.size() < currentLap-1) {
//                        m_fuelConsummed.add(fuelUsed);
//                    }
//                }
//                m_fuelAtStartFinish = m_fuelLevel;
//            }
//        }

        //update the caution lap counter, if we're under caution
        if ((m_sessionFlags & SessionFlags.caution) != 0
        ||  (m_sessionFlags & SessionFlags.cautionWaving) != 0
        ) {
            //don't count the lap the caution came out on if you want it to match iRacing's stats.
            if (!(   (prevSessionFlags & SessionFlags.caution) != 0
                  || (prevSessionFlags & SessionFlags.cautionWaving) != 0
                 )
            ) {
                m_lastCautionLap = currentLap;
            }
            else
            //don't count the same lap twice
            if (m_lastCautionLap != currentLap) {
                m_cautionLaps++;
                m_lastCautionLap = currentLap;
            }
        }

        //if previous tick was not under caution
        //and this one is, increment the caution counter
        if ((prevSessionFlags & SessionFlags.caution) == 0
        &&  (prevSessionFlags & SessionFlags.cautionWaving) == 0
        &&  ((m_sessionFlags & SessionFlags.caution) != 0 || (m_sessionFlags & SessionFlags.cautionWaving) != 0)
        ) {
            m_cautions += 1;
        }
        
        //bring the array up to the current lap if needed.
        while (m_invalidLaps.size() < (currentLap-1))
            m_invalidLaps.add(true);    //if we just got into the session, mark the earlier laps invalid.
        if (m_invalidLaps.size() < currentLap)
            m_invalidLaps.add(false);   //mark the current lap valid until proven guilty.

//if (isME())
//    currentLap=currentLap;

        //Did the car just cross the finish line and the race isn't over?
        if (m_lapCompletedPercent   >= 0.0 && m_lapCompletedPercent   < 0.1
        &&  prevLapCompletedPercent >= 0.9 && prevLapCompletedPercent < 1.0
        &&  m_sessionEndTime <= 0.0
        ) {
//if (isME())
//    currentLap=currentLap;

            //TODO: interpolate this using prevSessionTime and the percentCompleted to get an better time at the start/finish
            double timeAtStartFinish = m_sessionTime < 0.0 ? 0.0 : m_sessionTime;
            m_timeRemainingAtStartFinish = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("SessionTimeRemain");
//m_timeRemainingAtStartFinish = (8.0 * 60.0) - timeAtStartFinish;
            
            if (currentLap <= 1) {
//                if (isME())
//                    currentLap=currentLap;
                m_sessionStartTime = timeAtStartFinish;
            }
            else
                if ((m_sessionFlags & SessionFlags.checkered) != 0)
                    m_sessionEndTime = timeAtStartFinish;

            //did we just complete a new lap?
            if (m_lapCompleted < (currentLap - 1)) {

                m_lapCompleted = currentLap - 1;

                while (m_timeAtStartFinish.size() < m_lapCompleted) {
                    m_timeAtStartFinish.add(timeAtStartFinish - m_sessionStartTime);
                }
                
                if (m_fuelAtStartFinish > -1.0) {
                    double fuelUsed = 0.0;
                    fuelUsed = m_fuelAtStartFinish - m_fuelLevel >= 0.0 ? m_fuelAtStartFinish - m_fuelLevel : 0.0;
                    while (m_fuelConsummed.size() < m_lapCompleted) {
                        m_fuelConsummed.add(fuelUsed);
                    }
                }
            }

            m_fuelAtStartFinish = m_fuelLevel;
        }
        else {
            if (m_sessionEndTime <= 0.0 && currentLap > 0)
                m_lapCompleted = currentLap - 1;
        }

        if (nextStatus.equals(iRacingCar.Status.INPITSTALL)
        ||  nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)
        ||  nextStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
        ) {
            //if the stall pit is behind the finish line, add one to the lap pitted so it will show the same as cars ahead of the finish line
            if (m_lapCompletedPercent > .75 && m_lapCompletedPercent <= 1.0) {  //TODO: get this from the Track Class as LastPitStall. This is good at Bristol.
                m_lapPitted = m_lapCompleted + 2;
            }
            else {
                if (isME())
                    m_lapPitted = m_lapCompleted + 1;
                else
                    m_lapPitted = m_lapCompleted + 1;
            }
        }

        //mark the lap as dead if you were on pit road at any point of the lap
        //or it's the first lap where we are not really up to speed.
        if (currentLap > 0 && !m_invalidLaps.get(currentLap-1)) {

            if (nextStatus.equals(iRacingCar.Status.INPITSTALL)
            ||  nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)
            ||  nextStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
            ||  nextStatus.equals(iRacingCar.Status.ONPITROAD)
            ||  nextStatus.equals(iRacingCar.Status.APPROACHINGPITS)
            ||  nextStatus.equals(iRacingCar.Status.LEAVINGPITS)
            ||  nextStatus.equals(iRacingCar.Status.OFFTRACK)
            || (m_sessionFlags & (SessionFlags.caution|SessionFlags.cautionWaving|SessionFlags.red)) != 0
               //on green, list track types where you are not up to speed at the line
            || ((m_sessionFlags & SessionFlags.green) != 0 && !(m_iRacingSIMPlugin.getSession().getTrack().getType().getString().equals(Track.Type.ROAD_COURSE)))
            ) {
                m_invalidLaps.set(currentLap-1, true);
            }
            else
            if (isME() && (m_sessionFlags & (
                                SessionFlags.yellow
                               |SessionFlags.yellowWaving
                               |SessionFlags.repair)
                ) != 0
            ) {
                m_invalidLaps.set(currentLap-1, true);
            }
        }

        if (isME() && m_isNewCar) {
//            //try and detect if a new car was given where you have full fuel, new tires
//            //not the type of reset where you get your repairs. I don't think that gives you fuel. It might give you tires
//            //Just need to know if you have new tires, so we can record the temps and wear in the history.
//
//            //detect reset, just entered pit stall with more fuel than before and it's near the max capacity
//            if (!m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)   //I was not in the pit stall the previous update
//            &&  nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)  //I'm currently entering the pit stall
//            &&  m_speed < 1.0                                            //I'm not moving
//            &&  fuelLevel > prevFuelLevel                              //I magically have more fuel
//            &&  fuelLevel > (getGauge(Gauge.Type.FUELLEVEL).getCapacityMaximum().getDouble() * .99) //the tank is full
//            ) {
//                isNewCar = true;
                  Server.logger().info(String.format("#%-3s (id=%d) New Car detected Entering Pit, prevStatus(%s) lap(%d) fuel(%f) prevFuel(%f) speed(%f), VarBufTick=%d",
                        m_number, m_id,
                        m_prevStatus.getState(),
                        m_lapPitted,
                        m_fuelLevel,prevFuelLevel,
                        this._getGauge(Gauge.Type.SPEEDOMETER).getValueCurrent().getDouble(),
                        m_iRacingSIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()));
//            }
        }

        //did we just enter the session?
        //Entered PITSTALL not from PITROAD in a Non-RACE session
        //Was not blinking
        //TODO: detect RESET activated, Hosted or CarbCup. build_2014_10_21 introduced ability to go to pits on practice without resetting.
        if (!nextStatus.equals(iRacingCar.Status.INVALID)
        && (   m_isNewCar
            || !m_initialReset
            || (  //entered pit stall from INVALID, means reset from somewhere on the track
                   (nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL) || nextStatus.equals(iRacingCar.Status.INPITSTALL))
                && (m_prevStatus.equals(iRacingCar.Status.INVALID) || m_prevStatus.equals(iRacingCar.Status.ONTRACK) || m_prevStatus.equals(iRacingCar.Status.OFFTRACK) || m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS))
                && !m_sessionType.equalsIgnoreCase("RACE")
               )
            || (  //entered track from INVALID, means start of the race on Track
                   nextStatus.equals(iRacingCar.Status.ONTRACK)
                && m_prevStatus.equals(iRacingCar.Status.INVALID)
                && m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox() == 1
                && m_sessionType.equalsIgnoreCase("RACE")
               )
            || (  //entered pit road from INVALID, means start of the race in the pits
                   nextStatus.equals(iRacingCar.Status.ONPITROAD)
                && m_prevStatus.equals(iRacingCar.Status.INVALID)
                && m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox() == 1
                && m_sessionType.equalsIgnoreCase("RACE")
               )
            || (  //entered pits from PITROAD, but now has more fuel. Reset on PITROAD while not in a Race
                   nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)
                && m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
                && m_fuelLevel > prevFuelLevel
                && !m_sessionType.equalsIgnoreCase("RACE")
               )
           )
        ) {
            //just entered pits from the outside world, what do we want to do? Reset?
            if (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Reset detected from(%s) to (%s) during (%s) autoResetPitBox(%d), autoResetFastRepair(%d), Lap=%d, VarBufTick=%d",
                    m_number, m_id,
                    m_prevStatus.getState(),
                    nextStatus.getState(),
                    m_sessionType,
                    m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox(),
                    m_iRacingSIMPlugin.getIODriver().getAutoResetFastRepair(),
                    m_lapPitted,
                    m_iRacingSIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()));

            this._resetDetected();
            
//            _setupReset(m_lapPitted, m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox(),m_iRacingSIMPlugin.getIODriver().getAutoResetFastRepair());

//as of the March 30, 2016 patch, this option is now available with app.ini[Pit Service]autoResetFastRepair            
//            //if we are on the track, turn off the fast repair flag
//            if (nextStatus.equals(iRacingCar.Status.ONTRACK)) {
//                if (Server.getArg("gauge-fastrepairs-autoreset-off",true))
//                   this._getGauge(Gauge.Type.FASTREPAIRS).setChangeFlag(false);
//            }
            
            m_initialReset = true;
            isReset = true;
            m_resetTime = m_sessionTime;
            m_repairTimeOptional = 0.0;
            m_repairTime = 0.0;
        }

        //here we want to keep the repair time because iRacing zero's it out when you leave the pits
        //even if you didn't complete the repairs
        double repairtime    = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("PitRepairLeft");
        if (repairtime > 0.01 || isReset || m_repairTime < 1.0 || nextStatus.equals(iRacingCar.Status.INGARAGE))
            m_repairTime = repairtime;
        double repairtimeopt = m_iRacingSIMPlugin.getIODriver().getVars().getDouble("PitOptRepairLeft");
        if (repairtimeopt > 0.01 || isReset || m_repairTimeOptional < 1.0 || nextStatus.equals(iRacingCar.Status.INGARAGE))
            m_repairTimeOptional = repairtimeopt;

        //if we just entered put road
        if (nextStatus.equals(iRacingCar.Status.ONPITROAD)
        && !m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
        ) {
//For now, let's not reissue the commands upon entering pit road
//With the 2015 december build, we not get all the values.            
//            if (!isReset && !m_iRacingSIMPlugin.getIODriver().build_january_6_2016())
//                m_forceSetupCommands = true;
        }

        //if we just entered the pit stall
        if ((nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL) && !m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL))
        || m_isNewCar
        ) {
            if (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Entering Pit Stall from(%s) during(%s) speed(%f), Lap=%d, VarBufTick=%d",
                    m_number,m_id,
                    m_prevStatus.getState(),
                    m_sessionType,
                    this._getGauge(Gauge.Type.SPEEDOMETER).getValueCurrent().getDouble(),
                    m_lapPitted,
                    m_iRacingSIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
                ));

//            _setupBeforePitting(m_lapPitted);
        }

        //is the car in the pit stall
        if (nextStatus.equals(iRacingCar.Status.INPITSTALL)) {

            //see if the location of the pit stall has not been set and set it.
            if (m_pitLocation < 0.0 || m_sessionVersion != m_iRacingSIMPlugin.getIODriver().getHeader().getSessionInfoUpdate()) {
//                if (!isME()) {
                    m_pitLocation = m_lapCompletedPercent;
//                }
            }
            
            m_stoppedInPitStall = true;
            //Now for the calculations when it's ME
            if (isME()) {
                //if fuel was added, update the baseline
                if (m_fuelLevel > m_fuelAtStartFinish)
                    m_fuelAtStartFinish = m_fuelLevel;


    //TODO: get the time entering and try to use that to time the pit stop
    //removing for now, not using
    //                    m_cache.getPitCommands().timeEnteringPits = getSessionTimeElapsed().getLong();

                if (m_fuelAtStartFinish == -1.0)
                    m_fuelAtStartFinish = m_fuelLevel;

                //Since iRacing doesn't support sending commands for the Tape,Wedge and BrakeBias
                //we will read the current values as they do get output when you make the changes in their black box

            } //isME()
        } //is the car in the pit stall

        //car is just exited the pit stall
        if (m_stoppedInPitStall
        &&  nextStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
        && !m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
        && this._getGauge(Gauge.Type.SPEEDOMETER).getValueCurrent().getDouble() > 0.0 //we're moving and not exiting the car
        ) {
            if (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Exiting Pits during(%s) time(%.1f) prevStatus(%s) status(%s) speed(%f) lap(%d), calling _setupTakeReading(), VarBufTick=%d",
                    m_number,m_id,
                    m_sessionType,
                    m_prevStatus.getTime(iRacingCar.Status.INPITSTALL, m_sessionTime),
                    m_prevStatus.getState(),
                    nextStatus.getState(),
                    this._getGauge(Gauge.Type.SPEEDOMETER).getValueCurrent().getDouble(),
                    m_lapPitted,
                    m_iRacingSIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
                ));

//            _setupTakeReading();
        }

        //if we stopped in the pit stall, then at some point when we're sure we are not going backwards, take the historical readings.
        if (m_stoppedInPitStall
        &&((    /*m_app_ini_autoResetPitBox == 0
                &&*/(
                        (  !nextStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
                        &&  m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
                        )
                    )
           )
//           ||
//           (
//                //any time you leave the pits
//                m_app_ini_autoResetPitBox == 1
//                && (
//                    (  !nextStatus.equals(iRacingCar.Status.LEAVINGPITS)
//                    &&  m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
//                    )
//                    || //sometimes leaving pits gets skipped if you're the first stall and you can get on track fast
//                    (   nextStatus.equals(iRacingCar.Status.ONTRACK)
//                    &&  m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
//                    )
//                    || //sometimes on pit road gets skipped if you're the first stall and you can get on track fast
//                    (   nextStatus.equals(iRacingCar.Status.ONTRACK)
//                    &&  m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
//                    )
//                )
//           )
          )
        ) {
            if  (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Leaving Pits during(%s) time(%.1f) prevStatus(%s) status(%s) speed(%f), calling _setupAfterPitting(Lap=%d), VarBufTick=%d",
                    m_number,m_id,
                    m_sessionType,
                    m_prevStatus.getTime(iRacingCar.Status.INPITSTALL, m_sessionTime),
                    m_prevStatus.getState(),
                    nextStatus.getState(),
                    this._getGauge(Gauge.Type.SPEEDOMETER).getValueCurrent().getDouble(),
                    m_lapPitted,
                    m_iRacingSIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
                ));

//            _setupAfterPitting(m_lapPitted);
//            m_forceSetupCommands = true;
            m_stoppedInPitStall = false;
        }
//        else //any time you leave the pits without stopping, iRacing resets the pit flags. We need to change them back
        //if you just entered the track and the autoResetPitBox is on, then call _setupReset() so by default everything is checked to be changed next stop
        if (/*!m_stoppedInPitStall
        &&*/ m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox() == 1
//        &&   nextStatus.equals(iRacingCar.Status.LEAVINGPITS)
//        &&   m_prevStatus.getTime(iRacingCar.Status.LEAVINGPITS, m_sessionTime) > AUTO_RESET_DELAY
        &&   nextStatus.equals(iRacingCar.Status.LEAVINGPITS)
        &&   (  m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
             || m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
             )
//        &&   nextStatus.equals(iRacingCar.Status.ONTRACK)
//        &&   (  m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
//             || m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
//             || m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
//             )
        ) {
            if (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Resetting after pitting for autoResetPitBox(1), autoResetFastRepair(%d) from(%s) to (%s) during (%s), calling _setupReset(Lap=%d), VarBufTick=%d",
                    m_number, m_id,
                    m_iRacingSIMPlugin.getIODriver().getAutoResetFastRepair(),
                    m_prevStatus.getState(),
                    nextStatus.getState(),
                    m_sessionType,
                    m_lapPitted,
                    m_iRacingSIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()));

//            _setupReset(m_lapPitted,m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox(),m_iRacingSIMPlugin.getIODriver().getAutoResetFastRepair());

//as of the March 30, 2016 patch, this option is now available with app.ini[Pit Service]autoResetFastRepair            
//            //turn off fast repairs even if user has auto reset = 1
//            if (Server.getArg("gauge-fastrepairs-autoreset-off",true))
//                this._getGauge(Gauge.Type.FASTREPAIRS).setChangeFlag(false);
            
            
            //m_forceSetupCommands = true;
        }
        else //any time you over shoot your pit without stopping and backup to pit road, iRacing resets the pit flags. We need to change them back
        if (!m_stoppedInPitStall
        &&   m_iRacingSIMPlugin.getIODriver().getAutoResetPitBox() == 1
        &&   nextStatus.equals(iRacingCar.Status.ONPITROAD)
        &&   (  m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
             )
        ) {
//            m_forceSetupCommands = true;
        }

        m_prevStatus.setState(nextStatus);

        //update the pit times array
        while (m_pitTimes.size() < m_lapCompleted || m_pitTimes.size() < m_lapPitted)
            m_pitTimes.add(0.0);
            
        m_pitTimes.set(m_lapPitted-1, Math.max(0.0,m_prevStatus.getTime(iRacingCar.Status.INPITSTALL,m_sessionTime) - EXIT_PIT_DELAY - 0.5) );

        //update our gauges
        Iterator<Entry<String,Gauge>> gauge_iter = m_gauges.entrySet().iterator();
        while (gauge_iter.hasNext()) {
            iRacingGauge gauge = (iRacingGauge)gauge_iter.next().getValue();
            gauge._onDataVersionChange(m_prevStatus,currentLap,m_sessionTime, m_lapCompletedPercent, m_trackLength.getDouble());
        }

        if (_sendSetupCommands()) {
            m_resetTime = m_sessionTime;
//            _setupBeforePitting(m_lapPitted);
        }
        
        m_sessionVersion = m_iRacingSIMPlugin.getIODriver().getHeader().getSessionInfoUpdate();
        m_isNewCar = false;
        return isValid();
    }

    @SuppressWarnings("unchecked")
    private void _initialize() {

        //See if the session is connected and pumping data
        if (m_iRacingSIMPlugin.getIODriver().getVarHeaders() == null
        ||  m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("SessionNum") == null
        ||  m_iRacingSIMPlugin.getIODriver().getSessionInfo() == null
        ||  m_iRacingSIMPlugin.getIODriver().getSessionInfo().getData() == null
        ||  ((Map<String,Map<String,Integer>>)m_iRacingSIMPlugin.getIODriver().getSessionInfo().getData()).get("DriverInfo") == null
        )
            return;

        int sessionNum = m_iRacingSIMPlugin.getIODriver().getVars().getInteger("SessionNum");

        if (m_iRacingSIMPlugin.getSession().getIsReplay().getBoolean())
            Server.logger().fine("Replay Mode Detected");
        
        //cache the session type so we aren't parsing in the Session String during these updates every time.
        m_sessionType = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(sessionNum),"SessionType").toUpperCase();

//        m_trackName  = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackName");
        m_trackType  = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackType");

        {
            String s[]   = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackPitSpeedLimit").split(" ");
            if (s.length == 2) {
                m_trackSpeedLimit = new Data("TrackPitSpeedLimit",Double.parseDouble(s[0]),s[1],Data.State.NORMAL);
            }
        }

        {
            String s[] = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackLength").split(" ");
            if (s.length == 2) {
                m_trackLength = new Data("TrackLength",Double.parseDouble(s[0]),s[1],Data.State.NORMAL);
            }
        }

        //Save the index to me
        m_ME = ((Map<String,Map<String,Integer>>)m_iRacingSIMPlugin.getIODriver().getSessionInfo().getData()).get("DriverInfo").get("DriverCarIdx");

        //if the car is not in the session, don't try to initialize it.
        if (m_id == -1)
            return;

        //These values are used a lot, so go ahead and cache them
        m_number     = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumber");

//for debugging
//if (m_number.equals("xx"))
//    m_number = m_number;

        String numberRaw  = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberRaw");
        if (!numberRaw.isEmpty())
            m_numberRaw = Integer.parseInt(numberRaw);
        //m_driverName = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserName");

        //get the lap completed from the results, then we will use current lap later to keep this updated.
        m_lapCompleted = m_results.getLapCompleted();

//        Map<String,Object> configMap = this._loadCar("com/SIMRacingApps/SIMPlugins/iRacing/Cars/"+m_name.replace(" ", "_")+".json");
//
//        if (configMap.containsKey("Description"))
//            m_description = (String)configMap.get("Description");

        //if the derived class did not override this, use the car short name from iRacing
        if (m_description.equals(m_name)) {
            m_description = m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassShortName");
        }

        Track track = m_iRacingSIMPlugin.getSession().getTrack();
        IODriver IODriver = m_iRacingSIMPlugin.getIODriver();
        
        Map<String,Map<String,Map<String,Object>>> simGaugesBefore = new HashMap<String, Map<String, Map<String, Object>>>();
        
        try {
            //The shift points in some cars are wrong. Therefore get the SIM value as the default and the JSON file can override them.
            //I know this is backwards and may not be true for all SIMs I will eventually impliment.
            
            //TODO: In a multiclass session, how to I get the RPM marks for each class?
            //      This is only good for the cars in the same class as me.
            double DriverCarSLShiftRPM = Double.parseDouble(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLShiftRPM"));
            double DriverCarRedLine    = Double.parseDouble(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarRedLine"));
            double DriverCarSLFirstRPM = Double.parseDouble(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLFirstRPM"));
            double DriverCarSLLastRPM  = Double.parseDouble(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLLastRPM"));
            double DriverCarSLBlinkRPM = Double.parseDouble(m_iRacingSIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLBlinkRPM"));
            
            Server.logger().info(String.format("iRacingCar._initialize() returned First=%.0f, Shift=%.0f, Last=%.0f, Blink=%.0f, RedLine=%.0f for #%s(%d) - %s",
                    DriverCarSLFirstRPM,
                    DriverCarSLShiftRPM,
                    DriverCarSLLastRPM,
                    DriverCarSLBlinkRPM,
                    DriverCarRedLine,
                    m_number,m_id,m_name));
            
            Map<String,Map<String,Object>> Tach_tracks = new HashMap<String,Map<String,Object>>();
            Map<String,Object> Tach_states = new HashMap<String,Object>();
            
            Map<String,Double> Tach_state = new HashMap<String,Double>();
            Tach_state.put("Start", DriverCarSLFirstRPM);
            Tach_state.put("End",   DriverCarSLShiftRPM);
            Tach_states.put("SHIFTLIGHTS", Tach_state);
            
            Tach_state = new HashMap<String,Double>();
            Tach_state.put("Start", DriverCarSLShiftRPM);
            Tach_state.put("End",   DriverCarSLBlinkRPM);
            Tach_states.put("SHIFT", Tach_state);
            
            Tach_state = new HashMap<String,Double>();
            Tach_state.put("Start", DriverCarSLBlinkRPM);
            Tach_state.put("End",   DriverCarRedLine);
            Tach_states.put("SHIFTBLINK", Tach_state);
            
            Tach_state = new HashMap<String,Double>();
            Tach_state.put("Start", DriverCarRedLine);
            Tach_state.put("End",   Double.MAX_VALUE);
            Tach_states.put("CRITICAL", Tach_state);

            Map<String,Object> default_track = new HashMap<String,Object>();
            default_track.put("States", Tach_states);
            
            Tach_tracks.put("default", default_track);
            
            simGaugesBefore.put(Gauge.Type.TACHOMETER, Tach_tracks);

        }
        catch (NumberFormatException e) {}
        
        if (isME()) {
            _setGauge(new iRacingGauge(Gauge.Type.ABS,                          this, track, IODriver, "dcABS", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.ABSACTIVE,                    this, track, IODriver, "BrakeABSactive", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.ANTIROLLFRONT,                this, track, IODriver, "dcAntiRollFront", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.ANTIROLLREAR,                 this, track, IODriver, "dcAntiRollRear", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.BRAKE,                        this, track, IODriver, "Brake", "%", null, null));
            _setGauge(new BrakeBias(Gauge.Type.BRAKEBIASADJUSTMENT,             this, track, IODriver));
            _setGauge(new iRacingGauge(Gauge.Type.BRAKEBIASFINEADJUSTMENT,      this, track, IODriver, "dcBrakeBiasFine", "", null, null));
            _setGauge(new BrakePressure(Gauge.Type.BRAKEPRESSURE,               this, track, IODriver));
            _setGauge(new iRacingGauge(Gauge.Type.BOOSTLEVEL,                   this, track, IODriver, "dcBoostLevel", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.CLUTCH,                       this, track, IODriver, "Clutch", "%", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.DIFFENTRY,                    this, track, IODriver, "dcDiffEntry", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.DIFFEXIT,                     this, track, IODriver, "dcDiffExit", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.DIFFMIDDLE,                   this, track, IODriver, "dcDiffMiddle", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.DIFFPRELOAD,                  this, track, IODriver, "dcDiffPreload", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.DISABLEFUELCUT,               this, track, IODriver, "dcFuelNoCutToggle", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.ENGINEBRAKING,                this, track, IODriver, "dcEngineBraking", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.ENGINEPOWER,                  this, track, IODriver, "dcEnginePower", "", null, null));
            _setGauge(new FastRepairs(Gauge.Type.FASTREPAIRS,                   this, track, IODriver));
            _setGauge(new iRacingGauge(Gauge.Type.FULLCOURSEYELLOWMODE,         this, track, IODriver, "dcFCYToggle", "", null, null));
            _setGauge(new Changeables(Gauge.Type.FRONTFLAP,                     this, track, IODriver, "dpFNOMKnobSetting", "", null, null));
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpWingFront") != null) {
                _setGauge(new iRacingGauge(Gauge.Type.FRONTWING,                this, track, IODriver, "dpWingFront", "deg", null, null));
            }
            else
            if (IODriver.getVarHeaders().getVarHeader("dpFWingAngle") != null) {
                _setGauge(new Changeables(Gauge.Type.FRONTWING,                 this, track, IODriver, "dpFWingAngle", "deg", null, null));
            }
            else
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpFWingSetting") != null) {
                _setGauge(new Changeables(Gauge.Type.FRONTWING,                 this, track, IODriver, "dpFWingSetting", "", null, null));
            }
            else {
                _setGauge(new Changeables(Gauge.Type.FRONTWING,                 this, track, IODriver, "dpFWingIndex", "deg", null, null));
            }
            _setGauge(new iRacingGauge(Gauge.Type.FUELCUTPOSITION,              this, track, IODriver, "dcFuelCutPosition", "", null, null));
            _setGauge(new FuelLevel(Gauge.Type.FUELLEVEL,                       this, track, IODriver, m_driversIdx));
            _setGauge(new iRacingGauge(Gauge.Type.FUELMIXTURE,                  this, track, IODriver, "dcFuelMixture", "", null, null));
            _setGauge(new FuelPressure(Gauge.Type.FUELPRESSURE,                 this, track, IODriver));
            _setGauge(new iRacingGauge(Gauge.Type.GEAR,                         this, track, IODriver, "Gear", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.HYSBOOSTHOLD,                 this, track, IODriver, "dcHysBoostHold", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.HYSCHARGE,                    this, track, IODriver, "EnergyERSBatteryPct", "%", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.HYSDEPLOYMENT,                this, track, IODriver, "EnergyMGU_KLapDeployPct", "%", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.HYSDEPLOYMODE,                this, track, IODriver, "dcMGUKDeployMode", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.HYSDEPLOYTRIM,                this, track, IODriver, "dcMGUKDeployFixed", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.HYSDISABLEBOOSTHOLD,          this, track, IODriver, "dcHysNoBoostToggle", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.HYSREGENGAIN,                 this, track, IODriver, "dcMGUKRegenGain", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.INLAPMODE,                    this, track, IODriver, "dcInLapToggle", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.LAUNCHRPM,                    this, track, IODriver, "dcLaunchRPM", "rpm", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.LOWFUELACCEPT,                this, track, IODriver, "dcLowFuelAccept", "", null, null));
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpLRWedgeAdj") != null)
                _setGauge(new Changeables(Gauge.Type.LRWEDGEADJUSTMENT,         this, track, IODriver, "dpLRWedgeAdj", "mm", null, null));
            else
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpLrWedgeAdj") != null)
                _setGauge(new Changeables(Gauge.Type.LRWEDGEADJUSTMENT,         this, track, IODriver, "dpLrWedgeAdj", "mm", null, null));
            else
                _setGauge(new Changeables(Gauge.Type.LRWEDGEADJUSTMENT,         this, track, IODriver, "dpWeightJackerLeft", "mm", null, null));
            
            _setGauge(new iRacingGauge(Gauge.Type.OILLEVEL,                     this, track, IODriver, "OilLevel", "l", null, null));
            _setGauge(new OilPressure(Gauge.Type.OILPRESSURE,                   this, track, IODriver));
            _setGauge(new iRacingGauge(Gauge.Type.OILTEMP,                      this, track, IODriver, "OilTemp", "C", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.PEAKBRAKEBIAS,                this, track, IODriver, "dcPeakBrakeBias", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.PITSPEEDLIMITER,              this, track, IODriver, "dcPitSpeedLimiterToggle", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.POWERSTEERINGASSIST,          this, track, IODriver, "dpPSSetting", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.RFBRAKECONNECTED,             this, track, IODriver, "dcRFBrakeAttachedToggle", "", null, null));
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpWingRear") != null) {
                _setGauge(new iRacingGauge(Gauge.Type.REARWING,                  this, track, IODriver, "dpWingRear", "mm", null, null));
            }
            else
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRWingAngle") != null) {
                _setGauge(new Changeables(Gauge.Type.REARWING,                  this, track, IODriver, "dpRWingAngle", "deg", null, null));
            }
            else
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRWingSetting") != null) {
                _setGauge(new Changeables(Gauge.Type.REARWING,                  this, track, IODriver, "dpRWingSetting", "", null, null));
            }
            else {
                _setGauge(new iRacingGauge(Gauge.Type.REARWING,                  this, track, IODriver, "dpRWingIndex", "deg", null, null));
            }
            
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRrPerchOffsetm") != null) {
                _setGauge(new Changeables(Gauge.Type.RRWEDGEADJUSTMENT,         this, track, IODriver, "dpRrPerchOffsetm", "mm", null, null));
            }
            else //check to see if the new value exists, otherwise use old value for recorded files.
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRRWedgeAdj") != null) {
                _setGauge(new Changeables(Gauge.Type.RRWEDGEADJUSTMENT,         this, track, IODriver, "dpRRWedgeAdj", "mm", null, null));
            }
            else
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRrWedgeAdj") != null) {
                _setGauge(new Changeables(Gauge.Type.RRWEDGEADJUSTMENT,         this, track, IODriver, "dpRrWedgeAdj", "mm", null, null));
            }
            else 
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpWedgeAdj") != null) {
                _setGauge(new Changeables(Gauge.Type.RRWEDGEADJUSTMENT,         this, track, IODriver, "dpWedgeAdj", "mm", null, null));
            }
            else {
                _setGauge(new Changeables(Gauge.Type.RRWEDGEADJUSTMENT,         this, track, IODriver, "dpWeightJackerRight", "mm", null, null));
            }
            
            _setGauge(new Speedometer(Gauge.Type.SPEEDOMETER,                   this, track, IODriver, "Speed", "km/h"));
            _setGauge(new iRacingGauge(Gauge.Type.STARTER,                      this, track, IODriver, "dcStarter", "", null, null));
            _setGauge(new Steering(Gauge.Type.STEERING,                         this, track, IODriver, "SteeringWheelAngle", "rad"));
            _setGauge(new Tachometer(Gauge.Type.TACHOMETER,                     this, track, IODriver, "RPM", "rev/min", simGaugesBefore));
            _setGauge(new Accelometer(Gauge.Type.ACCELOMETER,                   this, track, IODriver, "RPM", "rev/min"));
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpQtape") != null)
                _setGauge(new Changeables(Gauge.Type.TAPE,                          this, track, IODriver, "dpQtape", "%", null, null));
            else
            if (m_iRacingSIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpQTape") != null)
                _setGauge(new Changeables(Gauge.Type.TAPE,                          this, track, IODriver, "dpQTape", "%", null, null));
            else
                _setGauge(new Changeables(Gauge.Type.TAPE,                          this, track, IODriver, "dcQTape", "%", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.THROTTLE,                     this, track, IODriver, "Throttle", "%", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.THROTTLESHAPE,                this, track, IODriver, "dcThrottleShape", "", null, null));
            
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPLFL,                      this, track, IODriver, "LF", "L"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPLFM,                      this, track, IODriver, "LF", "M"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPLFR,                      this, track, IODriver, "LF", "R"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPRFL,                      this, track, IODriver, "RF", "L"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPRFM,                      this, track, IODriver, "RF", "M"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPRFR,                      this, track, IODriver, "RF", "R"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPLRL,                      this, track, IODriver, "LR", "L"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPLRM,                      this, track, IODriver, "LR", "M"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPLRR,                      this, track, IODriver, "LR", "R"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPRRL,                      this, track, IODriver, "RR", "L"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPRRM,                      this, track, IODriver, "RR", "M"));
            _setGauge(new TireTemp(Gauge.Type.TIRETEMPRRR,                      this, track, IODriver, "RR", "R"));
            
            _setGauge(new TireWear(Gauge.Type.TIREWEARLFL,                      this, track, IODriver, "LF", "L"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARLFM,                      this, track, IODriver, "LF", "M"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARLFR,                      this, track, IODriver, "LF", "R"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARRFL,                      this, track, IODriver, "RF", "L"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARRFM,                      this, track, IODriver, "RF", "M"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARRFR,                      this, track, IODriver, "RF", "R"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARLRL,                      this, track, IODriver, "LR", "L"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARLRM,                      this, track, IODriver, "LR", "M"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARLRR,                      this, track, IODriver, "LR", "R"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARRRL,                      this, track, IODriver, "RR", "L"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARRRM,                      this, track, IODriver, "RR", "M"));
            _setGauge(new TireWear(Gauge.Type.TIREWEARRRR,                      this, track, IODriver, "RR", "R"));
            
            _setGauge(new TireCompound(Gauge.Type.TIRECOMPOUND,                 this, track, IODriver));
            
            _setGauge(new TirePressure(Gauge.Type.TIREPRESSURELF,               this, track, IODriver, "LF",
                 (TireTemp)_getGauge(Gauge.Type.TIRETEMPLFL),
                 (TireTemp)_getGauge(Gauge.Type.TIRETEMPLFM),
                 (TireTemp)_getGauge(Gauge.Type.TIRETEMPLFR),
                 (TireWear)_getGauge(Gauge.Type.TIREWEARLFL),
                 (TireWear)_getGauge(Gauge.Type.TIREWEARLFM),
                 (TireWear)_getGauge(Gauge.Type.TIREWEARLFR),
                 (TireCompound)_getGauge(Gauge.Type.TIRECOMPOUND)
            ));
            _setGauge(new TirePressure(Gauge.Type.TIREPRESSURERF,               this, track, IODriver, "RF",
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPRFL),
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPRFM),
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPRFR),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARRFL),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARRFM),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARRFR),
                    (TireCompound)_getGauge(Gauge.Type.TIRECOMPOUND)
            ));
            _setGauge(new TirePressure(Gauge.Type.TIREPRESSURELR,               this, track, IODriver, "LR",
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPLRL),
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPLRM),
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPLRR),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARLRL),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARLRM),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARLRR),
                    (TireCompound)_getGauge(Gauge.Type.TIRECOMPOUND)
            ));
            _setGauge(new TirePressure(Gauge.Type.TIREPRESSURERR,               this, track, IODriver, "RR",
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPRRL),
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPRRM),
                    (TireTemp)_getGauge(Gauge.Type.TIRETEMPRRR),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARRRL),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARRRM),
                    (TireWear)_getGauge(Gauge.Type.TIREWEARRRR),
                    (TireCompound)_getGauge(Gauge.Type.TIRECOMPOUND)
            ));

            _setGauge(new iRacingGauge(Gauge.Type.TOPWING,                      this, track, IODriver, "dcWingRear", "mm", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.TRACTIONCONTROLFRONT,         this, track, IODriver, "dcTractionControl2", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.TRACTIONCONTROLREAR,          this, track, IODriver, "dcTractionControl", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.TRACTIONCONTROL,              this, track, IODriver, "dcTractionControlToggle", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.VOLTAGE,                      this, track, IODriver, "Voltage", "v", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.WATERLEVEL,                   this, track, IODriver, "WaterLevel", "l", null, null));
            _setGauge(new WaterPressure(Gauge.Type.WATERPRESSURE,               this, track, IODriver));
            _setGauge(new iRacingGauge(Gauge.Type.WATERTEMP,                    this, track, IODriver, "WaterTemp", "C", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.WEIGHTJACKERLEFT,             this, track, IODriver, "dcWeightJackerLeft", "", null, null));
            _setGauge(new iRacingGauge(Gauge.Type.WEIGHTJACKERRIGHT,            this, track, IODriver, "dcWeightJackerRight", "", null, null));
            _setGauge(new WindshieldTearoff(Gauge.Type.WINDSHIELDTEAROFF,       this, track, IODriver));
            
//            dumpGauges();
        }
        else {
            //not ME, these are the only active gauges for the other cars.
            _setGauge(new iRacingGauge(Gauge.Type.GEAR,                         this, track, IODriver, "CarIdxGear", "", null, null));
            _setGauge(new Speedometer(Gauge.Type.SPEEDOMETER,                   this, track, IODriver, "Speed", "km/h"));
            _setGauge(new Steering(Gauge.Type.STEERING,                         this, track, IODriver, "CarIdxSteer", "rad"));
            _setGauge(new Tachometer(Gauge.Type.TACHOMETER,                     this, track, IODriver, "CarIdxRPM", "rev/min", simGaugesBefore));
        }
        
        _postInitialization();
    }
}
