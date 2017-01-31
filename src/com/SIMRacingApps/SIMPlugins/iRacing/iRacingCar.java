package com.SIMRacingApps.SIMPlugins.iRacing;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Gauge;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Session;
import com.SIMRacingApps.SIMPlugins.iRacing.VarData.*;
import com.SIMRacingApps.SIMPlugins.iRacing.SessionFlags;
import com.SIMRacingApps.SIMPlugins.iRacing.TrackSurface;
import com.SIMRacingApps.SIMPlugins.iRacing.VarHeaders.VarHeader;
import com.SIMRacingApps.Session.Type;
import com.SIMRacingApps.Util.FindFile;
import com.SIMRacingApps.Util.State;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class iRacingCar extends Car {
    
    private final double ENTER_PIT_DELAY   = 2.0;  //seconds to wait when entering pits before we're sure they stopped and did not over shoot the pit stall
                                                   //this was set to 2.0, but the Dallara was sending the tire readings before that
    private final double EXIT_PIT_DELAY    = 1.0;  //seconds to wait to confirm we have exited the pits long enough before resetting the pit black boxes and updating the historical values.
    private final double RESET_PIT_DELAY   = 0.3;  //seconds to wait after leaving pit road to send pit commands
    private final double INVALID_INPITSTALL= 1.0;  //seconds to wait before declaring invalid while in pit stall
//    private final double AUTO_RESET_DELAY  = 5.0;  //seconds to wait after leaving pits to change any pit commands because we have to wait on iRacing to do it first.

//    protected String m_trackName           = "";
    protected String m_trackType           = "";
    protected Data   m_trackSpeedLimit     = new Data("TrackPitSpeedLimit",0.0,"km/h");
    protected Data   m_trackLength         = new Data("TrackLength",0.0,"km");
    protected double m_sessionTime         = 0.0;
    protected double m_sessionStartTime    = 0.0;
    protected double m_sessionEndTime      = 0.0;
    protected String m_sessionType         = "";
    protected int    m_sessionVersion      = -1;
    protected boolean m_initialReset       = false;
    protected double m_resetTime           = 0.0; 
    protected boolean m_enableSIMSetupCommands = false;
    protected boolean m_forceSetupCommands  = false;
    protected boolean m_stoppedInPitStall   = false;
    
//    protected int   m_app_ini_autoResetPitBox=1;    //iRacing defaults to 1
    private iRacingSIMPlugin m_SIMPlugin;
    private Integer m_driversIdx            = -1;   //The index of the car in the DriverInfo.Drivers[] array.
    private String  m_driverName            = "";
    private String  m_number                = "";
    private Integer m_numberRaw             = -1;

    private State   m_prevStatus            = new State(Car.Status.INVALID,0.0);
    private State   m_surfacelocation       = new State("",0.0);
    private int     m_discontinuality       = -1;  //start out -1, so the first time we enter the track it's not counted.
    private double  m_fuelLevel             = 0.0;
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
    private int     m_displayUnits          = -1; 
    private String  m_gear                  = "";   //keep track of what gear we are in to detect when we change gears
    private String  m_power                 = "";   //keep track of what power we are in
    private int     m_lastKnownRadio        = 0;
    private int     m_lastKnownFrequency    = 0;    //Keep track of the last frequency they transmitted on.
    private int     m_lastKnownIncidents    = 0;
    private int     m_lastKnownIncidentsTeam= 0;

    private VarDataDouble m_fuelReader;
    private VarDataDoubleSpeed m_speedReader;
    
    protected _Results m_results = new _Results();
    protected _ResultsQualifying m_resultsQualifying = new _ResultsQualifying();

    public iRacingCar(iRacingSIMPlugin SIMPlugin) {
        super(SIMPlugin);
        m_SIMPlugin = SIMPlugin;
        _initialize();
    }

    public iRacingCar(iRacingSIMPlugin SIMPlugin, Integer id, String name, Integer driversIdx) {
        super(SIMPlugin, id, name);
        m_SIMPlugin = SIMPlugin;
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
            if (m_sessionVersion == m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate() || m_id == -1)
                return;

            m_sessionVersion = m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate();

            try {
                for (int i = 0;;i++) {
                    String sCarIdx = m_SIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"CarIdx");
                    if (sCarIdx.isEmpty()) {
                        break;
                    }
                    else
                    if (Integer.parseInt(sCarIdx) == m_id) {
                        String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"FastestTime");
                        if (!s.isEmpty() && Double.parseDouble(s) > 0.0) {
                            m_lapTimeBest = Double.parseDouble(s);
                            s = m_SIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"FastestLap");
                            if (!s.isEmpty())
                                m_lapBest     = Integer.parseInt(s);
                        }
                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"Position");
                        if (!s.isEmpty() && Integer.parseInt(s) >= 0)
                            m_position    = Integer.parseInt(s) + 1;  //Qual positions are zero based

                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"ClassPosition");
                        if (!s.isEmpty() && Integer.parseInt(s) >= 0) {
                            if (m_SIMPlugin.getIODriver().build_december_9_2014())
                                m_positionClass    = Integer.parseInt(s) + 1;
                            else
                            if (m_SIMPlugin.getIODriver().build_november_12_2014())
                                m_positionClass    = Integer.parseInt(s);
                            else
                                m_positionClass    = Integer.parseInt(s) + 1;
                        }
                        else
                            m_positionClass    = m_position;
                        
                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("QualifyResultsInfo","Results",Integer.toString(i),"Incidents");
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
        private int     m_incidents             = 0;    public int getIncidents()      { _refresh(); return m_incidents; }
        
        private ArrayList<Double>  m_lapTimes        = new ArrayList<Double>();  public ArrayList<Double>  getLapTimes()       { _refresh(); return m_lapTimes; } //indexed by lap completed, zero based (e.g. Lap 1 = index 0)
        private ArrayList<Integer> m_positions       = new ArrayList<Integer>(); public ArrayList<Integer> getPositions()      { _refresh(); return m_positions; } //indexed by lap completed, zero based (e.g. Lap 1 = index 0)
        private ArrayList<Integer> m_positionsClass  = new ArrayList<Integer>(); public ArrayList<Integer> getPositionsClass() { _refresh(); return m_positionsClass; } //indexed by lap completed, zero based (e.g. Lap 1 = index 0)

        public _Results() {}
        private void _refresh() {
            int m_position_2015 = -1;
            int m_positionClass_2015 = -1;
            

//These were causing funny results. Don't need them since it works from the session string without them.            
//            if (m_SIMPlugin.getIODriver().build_June_9_2015() && m_id >= 0) {
//                //according to a post in the forums, this is updated during a replay while the session info isn't. TODO: Verify
//                m_position_2015      = m_SIMPlugin.getIODriver().getVars().getInteger("CarIdxPosition",m_id);
//                m_positionClass_2015 = m_SIMPlugin.getIODriver().getVars().getInteger("CarIdxClassPosition",m_id);
//            }
            
            if (m_id == -1 || m_sessionVersion == m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate())
                return;

            m_sessionVersion = m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate();

            //our position in the results array could change every update, so we have to scan the array for this car every time
            int index = -1;
            int resultsCount = 0;

            try {
                for (int i = 0;;i++) {
                    String sCarIdx = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(i),"CarIdx");

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
                //String sessionType = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum")),"SessionType").toUpperCase();

                if (resultsCount > 0) {
                    if (index == -1) { //if we haven't posted a lap yet, zero out the position so qualifying doesn't bleed into the race.
                        m_position    = 0;
                        m_positionClass = 0;
                        m_lapTimeBest = 0.0;
                        m_lapBest     = 0;
                        m_lapsLed     = 0;
                    }
                    else {
                        String s;
                        if (m_position_2015 > 0) {  //if new value is valid use it
                            m_position = m_position_2015;
                        }
                        else {
                            s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"Position");
                            if (!s.isEmpty() && Integer.parseInt(s) > 0)
                                m_position      = Integer.parseInt(s);
                        }
                        
                        if (m_positionClass_2015 > 0 ) {  //if new value is valid use it
                            m_positionClass = m_positionClass_2015;
                        }
                        else {
                            s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"ClassPosition");
                            if (!s.isEmpty() && Integer.parseInt(s) >= 0) {
                                if (m_SIMPlugin.getIODriver().build_december_9_2014())
                                    m_positionClass = Integer.parseInt(s) + 1;
                                else
                                if (m_SIMPlugin.getIODriver().build_november_12_2014())
                                    m_positionClass = Integer.parseInt(s);
                                else
                                    m_positionClass = Integer.parseInt(s) + 1;
                            }
                            else
                                m_positionClass = m_position;
                        }

                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"LapsComplete");
                        if (!s.isEmpty() && Integer.parseInt(s) > 0.0) {
                            //do this for when we've just entered the session and we don't know the laps completed.
                            if (m_lapCompleted < Integer.parseInt(s)) {
                                
                                m_lapCompleted = Integer.parseInt(s);

                                //now save this last known position for this lap has history
                                while (m_positions.size() < m_lapCompleted) {
                                    m_positions.add(m_position);
                                }
                                while (m_positionsClass.size() < m_lapCompleted) {
                                    m_positionsClass.add(m_positionClass);
                                }
                            }
                        }

                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"LapsLed");
                        if (!s.isEmpty() && Integer.parseInt(s) > 0.0) {
                            //do this for when we've just entered the session and we don't know the laps completed.
                            if (m_lapsLed < Integer.parseInt(s))
                                m_lapsLed = Integer.parseInt(s);
                        }
                        
                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"Incidents");
                        if (!s.isEmpty() && Integer.parseInt(s) >= 0) {
                            m_incidents = Integer.parseInt(s);
                        }

                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"LastTime");
                        if (!s.isEmpty() /* && Double.parseDouble(s) > 0.0 *can get zero if meatball is out*/) {
                            m_lapTimeLast = Double.parseDouble(s);
                            
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

                        s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"FastestTime");
                        if (!s.isEmpty() && Double.parseDouble(s) > 0.0) {
                            m_lapTimeBest   = Double.parseDouble(s);
                            s = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",m_SIMPlugin.getIODriver().getVars().getString("SessionNum"),"ResultsPositions",Integer.toString(index),"FastestLap");
                            if (!s.isEmpty())
                                m_lapBest       = Integer.parseInt(s);
                        }
                        
                    }
                }
                else {
//                    if (sessionType.equalsIgnoreCase("RACE")) { //use qualifying results if session is RACE
                        m_position      = m_resultsQualifying.getPosition();
                        m_positionClass = m_resultsQualifying.getPositionClass();
                        m_lapTimeBest   = m_resultsQualifying.getLapTimeBest();
                        m_lapBest       = m_resultsQualifying.getLapBest();
                        m_incidents     = m_resultsQualifying.getIncidents();
//                    }
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
        || !m_SIMPlugin.getIODriver().getVars().getBoolean("IsOnTrack")
        //TODO: test pit commands if your a Crew Chief for another driver.
        )
            return false;

        //This gets called every tick to send the values to the SIM
        Gauge fuellevel = _getGauge(Gauge.Type.FUELLEVEL);
        Gauge tearoff   = _getGauge(Gauge.Type.WINDSHIELDTEAROFF);
        Gauge LF        = _getGauge(Gauge.Type.TIREPRESSURELF);
        Gauge LR        = _getGauge(Gauge.Type.TIREPRESSURELR);
        Gauge RF        = _getGauge(Gauge.Type.TIREPRESSURERF);
        Gauge RR        = _getGauge(Gauge.Type.TIREPRESSURERR);
        Gauge FR        = _getGauge(Gauge.Type.FASTREPAIRS);

        //test these as a group because if any are sent, we start with a #clear, otherwise nothing is sent
        if (!tearoff._getIsSentToSIM()
        ||  !fuellevel._getIsSentToSIM()
        ||  !LF._getIsSentToSIM()
        ||  !LR._getIsSentToSIM()
        ||  !RF._getIsSentToSIM()
        ||  !RR._getIsSentToSIM()
        ||  !FR._getIsSentToSIM()
        ||  m_forceSetupCommands
           ) {

            BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_Clear);
            Server.logger().info(String.format("_sendSetupCommands() Clear"));

            if (fuellevel.getChangeFlag().getBoolean() && fuellevel.getValueNext().getDouble() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%f/%s",fuellevel.getType().getString(),fuellevel.getValueNext().getDouble(),fuellevel.getValueNext().getUOM())));
                BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_Fuel, fuellevel.getValueNext());
            }

            if (LF.getChangeFlag().getBoolean() && LF.getValueNext().getDouble() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%f/%s",LF.getType().getString(),LF.getValueNext().getDouble(),LF.getValueNext().getUOM())));
                BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_LF, LF.getValueNext());
            }
            if (LR.getChangeFlag().getBoolean() && LR.getValueNext().getDouble() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%f/%s",LR.getType().getString(),LR.getValueNext().getDouble(),LR.getValueNext().getUOM())));
                BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_LR, LR.getValueNext());
            }
            if (RF.getChangeFlag().getBoolean() && RF.getValueNext().getDouble() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%f/%s",RF.getType().getString(),RF.getValueNext().getDouble(),RF.getValueNext().getUOM())));
                BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_RF, RF.getValueNext());
            }
            if (RR.getChangeFlag().getBoolean() && RR.getValueNext().getDouble() > 0.0) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setNextValue/%f/%s",RR.getType().getString(),RR.getValueNext().getDouble(),RR.getValueNext().getUOM())));
                BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(), BroadcastMsg.PitCommandMode.PitCommand_RR, RR.getValueNext());
            }

            //if user wants a tearoff or we are doing any other changes
            if (tearoff.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setChangeFlag/Y",tearoff.getType().getString())));
                BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(),BroadcastMsg.PitCommandMode.PitCommand_WS);
            }

            if (FR.getChangeFlag().getBoolean()) {
                Server.logger().info(String.format("_sendSetupCommands() %s", String.format("Car/REFERENCE/Gauge/%s/setChangeFlag/Y",FR.getType().getString())));
                BroadcastMsg.PitCommandMode.send(m_SIMPlugin.getIODriver(),BroadcastMsg.PitCommandMode.PitCommand_FR);
            }
            
            //set these as a group
            tearoff._setIsSentToSIM(true);
            fuellevel._setIsSentToSIM(true);
            LF._setIsSentToSIM(true);
            LR._setIsSentToSIM(true);
            RF._setIsSentToSIM(true);
            RR._setIsSentToSIM(true);
            FR._setIsSentToSIM(true);
            m_forceSetupCommands = false;
            return true;
        }
        return false;
    }

    private void _gaugeUpdateCurrentLap(int currentLap) {
        Iterator<Entry<String,Gauge>> gauge_iter = m_gauges.entrySet().iterator();
        while (gauge_iter.hasNext()) {
            Gauge gauge = gauge_iter.next().getValue();
            gauge.updateCurrentLap(currentLap);
        }
    }

    private static FindFile m_clubnames = null;
    
    @SuppressWarnings("unchecked")
    private int _getClubNumber(String club) {
        //I go this list from the Club drop down at http://members.iracing.com/membersite/member/statsseries.jsp
        if (club.isEmpty())
            return 0;
        
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
                return map.get("id").intValue();
            }
        }
        
        Server.logger().fine("Unknown Club Found, "+club);
        
        return 0;
    }
    private void _setupBeforePitting(int lap) {
        Iterator<Entry<String,Gauge>> gauge_iter = m_gauges.entrySet().iterator();
        while (gauge_iter.hasNext()) {
            Gauge gauge = gauge_iter.next().getValue();
            gauge.beforePitting(lap);
        }
    }

    private void _setupTakeReading() {
        Iterator<Entry<String,Gauge>> gauge_iter = m_gauges.entrySet().iterator();
        while (gauge_iter.hasNext()) {
            Gauge gauge = gauge_iter.next().getValue();
            gauge.takeReading();
        }
    }

    private void _setupAfterPitting(int lap) {
        Iterator<Entry<String,Gauge>> gauge_iter = m_gauges.entrySet().iterator();
        while (gauge_iter.hasNext()) {
            Gauge gauge = gauge_iter.next().getValue();
            gauge.afterPitting(lap);
        }
    }

    private void _setupReset(int lap, int app_ini_autoResetPitBox, int app_ini_autoResetFastRepair) {
        Iterator<Entry<String,Gauge>> gauge_iter = m_gauges.entrySet().iterator();
        while (gauge_iter.hasNext()) {
            Gauge gauge = gauge_iter.next().getValue();
            gauge.reset(lap,app_ini_autoResetPitBox,app_ini_autoResetFastRepair);
        }
    }

    private void _setupDefaultUOM(int displayUnits) {
        if (displayUnits > -1) {
            Iterator<Entry<String,Gauge>> gauge_iter = m_gauges.entrySet().iterator();
            while (gauge_iter.hasNext()) {
                Gauge gauge = gauge_iter.next().getValue();
                gauge.setDefaultUOM(displayUnits == 1 ? "METRIC" : "IMPERIAL");
            }
        }
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

        if (isValid()) {
            String color = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassColor");
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

        if (isValid()) {
            String name = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassShortName");
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

        // CarDesignStr: 0,000000,55040d,ffffff,ed2129
        // the last value is optional and sometimes missing. It is the tire rim color.
        // the color of the car is the 2nd number.

        if (isValid() && !isPaceCar()) {
            String design = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarDesignStr");
            String s[]    = design.split(",");
            if (s.length > 1)
                d.setValue(Integer.decode("0x"+s[1]),"RGB",Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getColorNumber() {
        Data d = super.getColorNumber();

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The color of the number is the 3rd number.
        if (isValid() && !isPaceCar()) {
            String design = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
            String s[]    = design.split(",");
            if (s.length > 2) {
                int color = Integer.decode("0x"+s[2]);
//                //if the Car Color and the Number Color is the same, invert it so we can see it
//                if (color == getColor().getInteger())
//                    color = getColor().getInteger() ^ 0xffffff;
                d.setValue(color,"RGB",Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getColorNumberBackground() {
        Data d = super.getColorNumberBackground();

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The color of the number's background is the 5th number.
        if (isValid() && !isPaceCar()) {
            String design = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
            String s[]    = design.split(",");
            if (s.length > 4) {
                d.setValue(Integer.decode("0x"+s[4]),"RGB",Data.State.NORMAL);
            }
            //if the Car Color background is the same as car's background invert the numbers background
            String cardesign = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarDesignStr");
            String s2[]    = cardesign.split(",");
            if (s2.length > 3) {
                if (d.getInteger() == Integer.decode("0x"+s[3]))
                    d.setValue(d.getInteger() ^ 0xffffff);
            }
        }
        return d;
    }

    @Override
    public Data getColorNumberOutline() {
        Data d = super.getColorNumberOutline();

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The color of the number's outline is the 4th number.
        if (isValid() && !isPaceCar()) {
            String design = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
            String s[]    = design.split(",");
            if (s.length > 3) {
                d.setValue(Integer.decode("0x"+s[3]),"RGB",Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getDescription() {
        Data d = super.getDescription();

        if (isValid()) {
            String desc = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarScreenName");
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

        if (isValid()) {
            d.setValue( m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"ClubName"),"",Data.State.NORMAL );
        }
        return d;
    }

    @Override
    public Data getDriverDivisionName() {
        Data d = super.getDriverDivisionName();

        if (isValid()) {
            d.setValue( m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"DivisionName"),"",Data.State.NORMAL );
        }
        return d;
    }

    @Override
    public Data getDriverInitials() {
        Data d = super.getDriverInitials();

        if (isValid()) {
            d.setValue( m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"Initials") );
        }
        return d;
    }

    @Override
    public Data getDriverLicenseColor() {
        Data d = super.getDriverLicenseColor();

        try {
            if (isValid()) {
                d.setValue(
                    Integer.parseUnsignedInt(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicColor"))
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
    public Data getDriverName() {
        Data d = super.getDriverName();
        d.setValue(m_driverName,"",Data.State.NORMAL);
        return d;
    }

    @Override
    public Data getDriverNameShort() {
        Data d = super.getDriverNameShort();
        String name = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"AbbrevName");
        d.setValue(name,"",Data.State.NORMAL);
        return d;
    }
    
    @Override
    public Data getDriverRating() {
        Data d = super.getDriverRating();

        try {
            if (isValid()) {
                String iRating      = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"IRating");
                Integer LicLevel    = Integer.parseInt(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicLevel"));
                Integer LicSubLevel = Integer.parseInt(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicSubLevel"));
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
    
                d.setValue(String.format("%s-%s%.2f",iRating,l,LicSubLevel/100.0));
                d.setState(Data.State.NORMAL);
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public Data getFuelLevelPerLap(int lapsToAverage, String UOM) {
        Data FuelPerLap  = super.getFuelLevelPerLap(lapsToAverage, UOM);

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

        if (m_SIMPlugin.getIODriver().isConnected()) {
            if (c > 0) {
                FuelPerLap.setValue(totalfuel/c,m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);
            }
            else {
                FuelPerLap.setValue(0.0,m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);
            }
        }
        
        FuelPerLap.addConversion(m_fuelReader);

        return FuelPerLap.convertUOM(_getGauge(Gauge.Type.FUELLEVEL).getUOM().getString()).convertUOM(UOM);
    }

    @Override
    public Data getFuelLevelNeeded(int lapsToAverage,double laps, String UOM) {
        Data d = super.getFuelLevelNeeded(lapsToAverage,laps,UOM);
        d.addConversion(m_fuelReader);
        return d.convertUOM(UOM);
    }
    
    @Override
    public Data getFuelLevelToFinish(int lapsToAverage,double laps, String UOM) {
        Data d = super.getFuelLevelToFinish(lapsToAverage,laps,UOM);
        d.addConversion(m_fuelReader);
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
        if (isME()) {
            if (!m_enableSIMSetupCommands)
                Server.logger().info(String.format("%s.getHasAutomaticPitCommands() is returning Y",this.getClass().getName()));
            m_enableSIMSetupCommands = true;
            d.setValue(true);
            d.setState(Data.State.NORMAL);
        }
        else
            d.setState(Data.State.NOTAVAILABLE);
        return d;
    }

    @Override
    public Data getId() {
        Data d = super.getId();
        if (isValid()) {
            d.setValue(m_id);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getImageUrl() {
        Data d = super.getImageUrl();

        try {
            if (isValid()) {
                //http://127.0.0.1:32034/car.png?dirpath=trucks%5Csilverado2015&size=2&pat=23&lic=feec04&car_number=1&colors=000000,cfcfcf,ff0600
    
                String CarDesignStr = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarDesignStr");
                String CarNumberDesignStr = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
                
                if (CarDesignStr.isEmpty())
                    CarDesignStr = "0,000000,ffffff,666666";    //a suitable default
                
                if (CarNumberDesignStr.isEmpty())
                    CarNumberDesignStr = "0,0,000000,ffffff,666666";    //a suitable default
                
                String parts[] = CarDesignStr.split("[,;]");
                
                if (parts.length > 3) {
                    String dirpath = this.getName().getString().replace(" ","%5C");
                    String pat = parts[0];
                    String lic = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicColor");
                    if (!lic.isEmpty()) 
                        lic = String.format("%06x",Integer.parseUnsignedInt(lic));
                    
                    String carSponser1 = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarSponsor_1");
                    String carSponser2 = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarSponsor_2");
                    String sponsors = (carSponser1.isEmpty() ? "0" : carSponser1) + "," + (carSponser2.isEmpty() ? "0" : carSponser2);
                    String club = Integer.toString(_getClubNumber(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"ClubName")));
                    String colors = parts[1]+","+parts[2]+","+parts[3];
                    String wheeltype = "0"; //TODO: wheel type, matt or chrome, is not output by iRacing as of May 2015, so use matt
                    String wheels = parts.length > 4 ? wheeltype + ","+parts[4] : wheeltype + ",000000";
                    
    //for now, iRacing always displays 45, so disable the car number                        
    //                String numparts[] = CarNumberDesignStr.split("[,;]");
    //                String car_number = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumber").replace("\"", "");
    //                String numfont = numparts.length > 0 ? numparts[0] : "0";
    //                String numslant = numparts.length > 1 ? numparts[1] : "0";
    //                String numcolors = numparts.length > 4 ? numparts[2]+","+numparts[3]+","+numparts[4] : "ffffff,777777,000000";
                    
                    //the caller must replace iRacing with the hostname and port of the iRacing server
                    //if running on the same machine, then it is 127.0.0.1::32034
                    String url = "iRacing/car.png"
                            + "?dirpath="+dirpath
                            + "&size=2"
                            + "&pat="+pat
                            + "&lic="+lic
    //                        + "&car_number="+car_number
    //                        + "&carnumber="+car_number
    //                        + "&numfont="+numfont
    //                        + "&numslant="+numslant
    //                        + "&numcolors="+numcolors
                            + "&colors="+colors
                            + "&sponsors="+sponsors
                            + "&club="+club
                            + "&wheels="+wheels
                    ;
                    d.setValue(url);
                    d.setState(Data.State.NORMAL);
                }
            }
        } catch (NumberFormatException e) {}
        
        return d;
    }
    
    @Override
    public Data getIncidents() {
        Data d = super.getIncidents();
        if (isValid()) {
            if (isME() && m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("PlayerCarDriverIncidentCount") != null) {
                int i = m_SIMPlugin.getIODriver().getVars().getInteger("PlayerCarDriverIncidentCount");
                if (i >= 0)
                    m_lastKnownIncidents = i;
                d.setValue(m_lastKnownIncidents,d.getUOM(),Data.State.NORMAL);
                
            }
            else
            if (m_sessionEndTime > 0.0) {
                d.setValue(m_results.getIncidents(),d.getUOM(),Data.State.NORMAL);
            }
        }
        return d;
    }

    @Override
    public Data getIncidentsTeam() {
        Data d = super.getIncidentsTeam();
        //only return a value if in a team session
        if (isValid()) {
            if (isME()) {
                if (!getTeamName().getString().isEmpty()
                &&  m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("PlayerCarTeamIncidentCount") != null) {
                    int i = m_SIMPlugin.getIODriver().getVars().getInteger("PlayerCarTeamIncidentCount");
                    if (i >= 0)
                        m_lastKnownIncidentsTeam = i;
                    d.setValue(m_lastKnownIncidentsTeam,d.getUOM(),Data.State.NORMAL);
                }
            }
        }
        return d;
    }

    @Override
    public Data getIsBlackFlag() {
        Data d = super.getIsBlackFlag();
        if (isValid() && isME()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.black) != 0 || (flags & SessionFlags.repair) != 0)
                d.setValue(true);
        }
        return d;
    }

    @Override
    public Data getIsBlueFlag() {
        Data d = super.getIsBlueFlag();
        if (isValid() && isME()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.blue) != 0)
                d.setValue(true);
        }
        return d;
    }

    @Override
    public Data getIsDisqualifyFlag() {
        Data d = super.getIsDisqualifyFlag();
        if (isValid() && isME()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.disqualify) != 0)
                d.setValue(true);
        }
        return d;
    }

    @Override
    public Data getIsDriving() {
        Data d = super.getIsDriving();
        if (isValid() && isME()) {
            boolean isOnTrack = m_SIMPlugin.getIODriver().getVars().getBoolean("IsOnTrack");
            d.setValue(isOnTrack,"boolean",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getIsFixedSetup() {
        Data d = super.getIsFixedSetup();
        try {
            if (isValid()) {
                String fixed = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","IsFixedSetup");
                if (Integer.parseInt(fixed) == 1)
                    d.setValue(true);
                else
                    d.setValue(false);
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public Data getIsPitSpeedLimiter() {
        Data d = super.getIsPitSpeedLimiter();
        if (isValid() && isME()) {
            if (getWarnings().getString().contains(";PITSPEEDLIMITER;"))
                d.setValue(true,"boolean",Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getIsSpectator() {
        Data d = super.getIsSpectator();
        try {
            if (isValid()) {
                String spectator = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"IsSpectator");
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
        if (isValid()) {
            d.setValue(isPaceCar(),"boolean",Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getIsYellowFlag() {
        Data d = super.getIsYellowFlag();
        if (isValid() && isME()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            if ((flags & SessionFlags.yellow) != 0 || (flags & SessionFlags.yellowWaving) != 0)
                d.setValue(true);
        }
        return d;
    }

    @Override
    public Data getLap(String ref) {
        Data d = super.getLap(ref);
        String r = d.getValue("reference").toString();

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
            d.setValue((m_lapCompleted + 1) - m_lapPitted + 1);
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
        if (m_SIMPlugin.isConnected()) {
            boolean isRace = m_SIMPlugin.getSession().getType().getString().equalsIgnoreCase(Type.RACE);
            boolean isQual = m_SIMPlugin.getSession().getType().getString().equalsIgnoreCase(Type.LONE_QUALIFY)
                          || m_SIMPlugin.getSession().getType().getString().equalsIgnoreCase(Type.OPEN_QUALIFY);
            
            //if this new value is in the telemetry, then return it. Otherwise fall back to calculating it.
            //It was added in the Dec 8, 2015 build. There was a variable called SessionLapsRemain that I never used in the previous build.
            /* This was overriding the ability to get the laps remaining based on time remaining. I will do it myself
            if (isRace && m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("SessionLapsRemainEx") != null) {
                int sessionLapsRemainEx = m_SIMPlugin.getIODriver().getVars().getInteger("SessionLapsRemainEx");
                d.setValue(sessionLapsRemainEx);
                d.setState(Data.State.NORMAL);
                return d;
            }
            */
            
            int laps       = m_SIMPlugin.getSession().getLaps().getInteger();
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
//double timeRemaining = m_SIMPlugin.getSession().getTimeRemaining().getDouble();
                double timeRemaining = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTimeRemain");
                
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
                            d.setUOM("~lap");
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
                }
            }

            //apply some boundries to the result
            togo = (int)Math.min(laps,Math.min(Math.max(0, togo),isRace || isQual ? laps - lap : laps));

            d.setValue(togo);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getLapTime(String ref) {
        Data d = super.getLapTime(ref);
        String r = d.getValue("reference").toString();

        if (isValid()) {
            if (r.equals(Car.LapType.CURRENT)) {
                double timeAtStartFinish = this.m_timeAtStartFinish.size() > 0 ? this.m_timeAtStartFinish.get(this.m_timeAtStartFinish.size()-1) : 0.0;
                
//TODO: Use LapCurrentLapTime. Currently it doesn't reset until 1 to 2 seconds after you cross the line
                if (!(timeAtStartFinish > 0.0 || m_sessionStartTime > 0.0) && isME()) {
                    double laptime = m_SIMPlugin.getIODriver().getVars().getDouble("LapCurrentLapTime");
                    d.setValue(laptime);
                    d.setUOM(m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapCurrentLapTime").Unit);
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
                    d.setUOM(m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapCurrentLapTime").Unit);
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
            String r = d.getValue("reference").toString();

            //spelling error in the variable name
            if (r.equals("SessionLast")) {
                //see if the bad name exists and use it. This will auto correct when it gets fixed
                if (m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapDeltaTo"+r+"lLap_OK") != null)
                    r = "SessionLastl";
            }

            if (m_SIMPlugin.getIODriver().getVars().getBoolean("LapDeltaTo"+r+"Lap_OK")) {
                double laptime = m_SIMPlugin.getIODriver().getVars().getDouble("LapDeltaTo"+r+"Lap");
                d.setValue(laptime);
                d.setState(Data.State.NORMAL);
            }
        }
        else
            d.setState(Data.State.NOTAVAILABLE);
        return d;
    }

    @Override
    public Data getLapTimeDeltaReference(String ref) {
        Data d = super.getLapTimeDeltaReference(ref);
        String r = d.getValue("reference").toString();
        Data t;
        //iRacing doesn't give me the reference value for all of them, so for the ones that don't use SessionBest
        if (r.equals("SessionLast"))
            t = getLapTime(r);
        else
            t = getLapTime("SessionBest");
        d.setValue(t.getValue());
        return d;
    }

    @Override
    public Data getLapTimeDeltaPercent(String ref) {
        Data d = super.getLapTimeDeltaPercent(ref);
        if (isME()) {
            String r = d.getValue("reference").toString();

            //spelling error in the variable name
            if (r.equals("SessionLast")) {
                //see if the bad name exists and use it. This will auto correct when it gets fixed
                if (m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("LapDeltaTo"+r+"lLap_OK") != null)
                    r = "SessionLastl";
            }

            if (m_SIMPlugin.getIODriver().getVars().getBoolean("LapDeltaTo"+r+"Lap_OK")) {
                double laptime = m_SIMPlugin.getIODriver().getVars().getDouble("LapDeltaTo"+r+"Lap_DD");
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
        else
            d.setState(Data.State.NOTAVAILABLE);
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
            VarHeader Lat = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("Lat",m_SIMPlugin.getIODriver().getVars());
            if (Lat != null)
                return new Data("/Car/I"+m_id+"/Latitude",Lat.Value,Lat.Unit,Data.State.NORMAL);
        }
        return super.getLatitude(UOM);
    }
    
    /**
     * iRacing only outputs this value in the IBT files for ME.
     * This code checks to see if it exists before returning it, otherwise it calls the base class.
     */
    @Override
    public Data getLongitude(String UOM) {
        if (Server.getArg("iracing-uselatlon",true) && isME()) { //allow an option to not use the iRacing Lat/Lon for testing
            VarHeader Lat = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("Lon",m_SIMPlugin.getIODriver().getVars());
            if (Lat != null)
                return new Data("/Car/I"+m_id+"/Longitude",Lat.Value,Lat.Unit,Data.State.NORMAL);
        }
        return super.getLongitude(UOM);
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
        if (m_SIMPlugin.isConnected() && isME()) {
            int flags = m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
            if (flags == -1)
                flags = 0;
            
            StringBuffer flagnames = new StringBuffer("");

            if ((flags & SessionFlags.repair) != 0)  { flagnames.append(";"); flagnames.append(Car.Message.REPAIR); }
            if (getIsPitSpeedLimiter().getBoolean()) { flagnames.append(";"); flagnames.append(Car.Message.PITSPEEDLIMITER); }
            flagnames.append(";");
            d.setValue(flagnames.toString());
            d.setState(Data.State.NORMAL);
        }
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
            String design = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
            String s[]    = design.split(",");
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

        // CarNumberDesignStr: 0,0,ffffff,777777,000000
        //The slant of the number is the 2rd number.
        //0=normal, 1=left, 2=right, 3=forward, 4=backwards
        if (isValid() && !isPaceCar()) {
            String design = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberDesignStr");
            String s[]    = design.split(",");
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
        d.setValue(m_prevStatus.getTime(iRacingCar.Status.INPITSTALL,m_sessionTime));
        d.setState(Data.State.NORMAL);
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
    public Data getRadioChannel() {
        Data d = super.getRadioChannel();
        if (isValid()) {
            if (isME()) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","SelectedRadioNum");
                s = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios",s,"TunedToFrequencyNum");
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
            
            String selectedRadio = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","SelectedRadioNum");

            if (!selectedRadio.isEmpty()) {
                Data channel = getRadioChannel();
                String name = m_SIMPlugin.getIODriver().getSessionInfo().getString("RadioInfo","Radios",Integer.toString(m_lastKnownRadio),"Frequencies",channel.getString(),"FrequencyName");
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
            int warnings     = m_SIMPlugin.getIODriver().getVars().getInteger("EngineWarnings");

            StringBuffer s = new StringBuffer(String.format("%d;0x%X;",warnings,warnings));
            if ((warnings & EngineWarnings.waterTempWarning) != 0)       s.append("WATERTEMPWARNING;") ;
            if ((warnings & EngineWarnings.fuelPressureWarning) != 0)    s.append("FUELPRESSUREWARNING;") ;
            if ((warnings & EngineWarnings.oilPressureWarning) != 0)     s.append("OILPRESSUREWARNING;") ;
            if ((warnings & EngineWarnings.engineStalled) != 0)          s.append("ENGINESTALLED;") ;
            if ((warnings & EngineWarnings.pitSpeedLimiter) != 0)        s.append("PITSPEEDLIMITER;") ;
            if ((warnings & EngineWarnings.revLimiterActive) != 0)       s.append("REVLIMITER;") ;

            if ((m_sessionFlags & SessionFlags.repair) != 0)             s.append("REPAIRSREQUIRED;");
            /*
                The following are not provided by iRacing, so we will derive them
                and in some cases fine tune them by car.
            */

        //WATER
            Data WaterTemp = _getGauge(Gauge.Type.WATERTEMP).getValueCurrent(); //new Data("WaterTemp",m_SIMPlugin.getIODriver().getVars().getDouble("WaterTemp"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("WaterTemp").Unit);

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

            Data WaterLevel = _getGauge(Gauge.Type.WATERLEVEL).getValueCurrent(); //new Data("WaterLevel",m_SIMPlugin.getIODriver().getVars().getDouble("WaterLevel"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("WaterLevel").Unit);

            if (WaterLevel.getState().equals(Data.State.CRITICAL))
                s.append("WATERLEVELCRITICAL;");
            else
            if (WaterLevel.getState().equals(Data.State.WARNING))
                s.append("WATERLEVELWARNING;");

        //OIL
            Data OilTemp = _getGauge(Gauge.Type.OILTEMP).getValueCurrent(); //new Data("OilTemp",m_SIMPlugin.getIODriver().getVars().getDouble("OilTemp"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("OilTemp").Unit);

            if (OilTemp.getState().equals(Data.State.CRITICAL))
                s.append("OILTEMPCRITICAL;");
            else
            if (OilTemp.getState().equals(Data.State.WARNING))
                s.append("OILTEMPWARNING;");

            Data OilPressure = _getGauge(Gauge.Type.OILPRESSURE).getValueCurrent(); //new Data("OilPress",m_SIMPlugin.getIODriver().getVars().getDouble("OilPress"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("OilPress").Unit);

            if (OilPressure.getState().equals(Data.State.CRITICAL))
                s.append("OILPRESSURECRITICAL;");
            else
            if (OilPressure.getState().equals(Data.State.WARNING))
                s.append("OILPRESSUREWARNING;");

            Data OilLevel = _getGauge(Gauge.Type.OILLEVEL).getValueCurrent(); //new Data("OilLevel",m_SIMPlugin.getIODriver().getVars().getDouble("OilLevel"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("OilLevel").Unit);

            if (OilLevel.getState().equals(Data.State.CRITICAL))
                s.append("OILLEVELCRITICAL;");
            else
            if (OilLevel.getState().equals(Data.State.WARNING))
                s.append("OILLEVELWARNING;");

        //FUEL
            Data FuelPressure = _getGauge(Gauge.Type.FUELPRESSURE).getValueCurrent(); //new Data("FuelPress",m_SIMPlugin.getIODriver().getVars().getDouble("FuelPress"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelPress").Unit);

            if (FuelPressure.getState().equals(Data.State.CRITICAL))
                s.append("FUELPRESSURECRITICAL;");
            else
            if (FuelPressure.getState().equals(Data.State.WARNING))
                s.append("FUELPRESSUREWARNING;");

            Data FuelLevel = _getGauge(Gauge.Type.FUELLEVEL).getValueCurrent(); //new Data("FuelLevel",m_SIMPlugin.getIODriver().getVars().getDouble("FuelLevel"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);

            if (FuelLevel.getState().equals(Data.State.CRITICAL))
                s.append("FUELLEVELCRITICAL;");
            else
            if (FuelLevel.getState().equals(Data.State.WARNING))
                s.append("FUELLEVELWARNING;");

        //VOLTAGE
            Data Voltage = _getGauge(Gauge.Type.VOLTAGE).getValueCurrent(); //new Data("Voltage",m_SIMPlugin.getIODriver().getVars().getDouble("Voltage"),m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("Voltage").Unit);

            if (Voltage.getState().equals(Data.State.CRITICAL))
                s.append("VOLTAGECRITICAL;");
            else
            if (Voltage.getState().equals(Data.State.WARNING))
                s.append("VOLTAGEWARNING;");
            d.setValue(s.toString());
            d.setState(Data.State.NORMAL);
        }
        else
            d.setState(Data.State.NOTAVAILABLE);
        return d;
    }

    public Data getDivisionName() {
        Data d = new Data("CarDivisionName","");
        d.setState(Data.State.NORMAL);

        if (isValid()) {
            d.setValue( m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"DivisionName") );
        }
        return d;
    }

    public Data getDriverLicLevel() {
        Data d = new Data("CarDriverLicLevel",0);
        d.setState(Data.State.NORMAL);

        try {
            if (isValid()) {
                d.setValue( Integer.parseInt(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicLevel")) );
            }
        } catch (NumberFormatException e) {}
        
        return d;
    }

    public Data getDriverLicSubLevel() {
        Data d = new Data("CarDriverLicLevel",0);
        d.setState(Data.State.NORMAL);

        try {
            if (isValid()) {
                d.setValue( Integer.parseInt(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"LicSubLevel")) );
            }
        } catch (NumberFormatException e) {}
        return d;
    }

    @Override
    public Data getFuelLevelAtStartFinish(String UOM) {
        Data d = super.getFuelLevelAtStartFinish(UOM);
        
        if (isValid()) {
            d.setValue(m_fuelAtStartFinish,m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("FuelLevel").Unit);
        }

        d.addConversion(m_fuelReader);
        
        return d.convertUOM(_getGauge(Gauge.Type.FUELLEVEL).getUOM().getString()).convertUOM(UOM);
    }

    @Override
    public Data getRepairTime() {
        Data d = super.getRepairTime();
        if (isME()) {
            d.setValue(m_repairTime);
            d.setUOM(m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitRepairLeft").Unit);
            d.setState(Data.State.NORMAL);
        }
        else
            d.setState(Data.State.NOTAVAILABLE);
        return d;
    }

    @Override
    public Data getRepairTimeOptional() {
        Data d = super.getRepairTimeOptional();
        if (isME()) {
            d.setValue(m_repairTimeOptional);
            d.setUOM(m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitOptRepairLeft").Unit);
            d.setState(Data.State.NORMAL);
        }
        else
            d.setState(Data.State.NOTAVAILABLE);
        return d;
    }

    @Override
    public Data getTeamName() {
        Data d = super.getTeamName();

        String teams = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TeamRacing");
        if (!teams.isEmpty() && Integer.parseInt(teams) != 0) {
            d.setValue( m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"TeamName") );
        }
        return d;
    }

    public boolean isPaceCar() {
        String username = getDriverName().getString();
        //In the Dec 2015 build, a flag was added to identify the pace car
        String isPaceCar = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarIsPaceCar");
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
        if (!m_SIMPlugin.getIODriver().isConnected())
            return false;

        if (m_name.equals("PITSTALL"))
            return true;

        if (super.isValid())
            return m_driversIdx != -1;
        return false;
    }

    //This version of isValid() is called by iRacingSIMPlugin._getNewData() to see if the car class needs to be reloaded.
    public boolean isValid(int id,int driversIdx) {
        if (super.isValid() && m_SIMPlugin.getIODriver().isConnected()) {
            if( m_id == id ) {
                if (m_driversIdx != -1) {
                    if (m_driversIdx == driversIdx) {
                        if (m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumber").equals(m_number)
                        &&  m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarPath").equals(m_name)
                        //&&  m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserName").equals(m_driverName)
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
        
        if (!m_number.isEmpty()) {
            if (onOffFlag) {
                d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "ADMIN").replace("[DRIVER]", m_number)).getString());
            }
            else {
                d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "NADMIN").replace("[DRIVER]", m_number)).getString());
            }
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data setBlackFlag(int quantity,String uom) {
        Data d = super.setBlackFlag(quantity,uom);

        if (!m_number.isEmpty()) {
            if (uom.equalsIgnoreCase("lap"))
                d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "BLACK").replace("[DRIVER]", m_number).replace("[TIME]", String.format("L%d", quantity))).getString());
            else
                d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "BLACK").replace("[DRIVER]", m_number).replace("[TIME]", String.format("%d", quantity))).getString());
    
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public    Data setCamera(String group, String camera) {
        Data d = super.setCamera(group,camera);
        
        if (isValid()) {
            Data s = m_SIMPlugin.getSession().setCamera("N"+Integer.toString(m_id), group, camera);
            d.setValue(s.getString(),s.getUOM(),s.getState());
        }
        
        return d;
    }
    
    @Override
    public    Data setChat(String text) {
        Data d = super.setChat(text);
        d.setValue(m_SIMPlugin.getSession().setChat(
            this.m_SIMPlugin.getSession().getSendKeys("CHAT", "DRIVER")
                .replace("[DRIVER]", m_number)
                .replace("[TEXT]",text)
        ).getString());
        d.setState(Data.State.NORMAL);
        return d;
    }

    @Override
    public    Data setChatFlag(boolean onOffFlag) {
        Data d = super.setChatFlag(onOffFlag);
        
        if (!m_number.isEmpty()) {
            if (onOffFlag) {
                d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "CHAT").replace("[DRIVER]", m_number)).getString());
            }
            else {
                d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "NCHAT").replace("[DRIVER]", m_number)).getString());
            }
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setClearPenaltiesFlag() {
        Data d = super.setClearPenaltiesFlag();
        
        if (!m_number.isEmpty()) {
            d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "CLEAR").replace("[DRIVER]", m_number)).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setDisqualifyFlag() {
        Data d = super.setDisqualifyFlag();
        
        if (!m_number.isEmpty()) {
            d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "DQ").replace("[DRIVER]", m_number)).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setEndOfLineFlag() {
        Data d = super.setEndOfLineFlag();
        
        if (!m_number.isEmpty()) {
            d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "EOL").replace("[DRIVER]", m_number)).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setRemoveFlag() {
        Data d = super.setRemoveFlag();
        
        if (!m_number.isEmpty()) {
            d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "REMOVE").replace("[DRIVER]", m_number)).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public    Data setWaveAroundFlag() {
        Data d = super.setWaveAroundFlag();
        
        if (!m_number.isEmpty()) {
            d.setValue(m_SIMPlugin.getSession().setChat(this.m_SIMPlugin.getSession().getSendKeys("ADMIN_COMMANDS", "WAVEBY").replace("[DRIVER]", m_number)).getString());
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    //This gets called every tick from the iRacingSIMPlugin loop.
    //Be careful not to put too much in here that will slow it down
    //read any values from the Session String in _initiallize() unless you think they will change. Then I would defer that read to the function that needs it.
    public boolean onDataVersionChange() {

        //double prevSessionTime         = m_sessionTime;
        double prevLapCompletedPercent = m_lapCompletedPercent;
        //int    prevLapCompleted        = m_lapCompleted;
        double lapCompletedPercent     = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLapDistPct") != null 
                                       ? m_SIMPlugin.getIODriver().getVars().getDouble("CarIdxLapDistPct", m_id)
                                       : (isME() ? m_SIMPlugin.getIODriver().getVars().getDouble("LapDistPct") : -1.0);
        double prevFuelLevel           = m_fuelLevel;
        double fuelLevel               = 0.0;
        int    prevSessionFlags        = m_sessionFlags;
        int    displayUnits            = m_SIMPlugin.getIODriver().getVars().getInteger("DisplayUnits");
        boolean isReset                = false;
        boolean isNewCar               = false; //TODO: Need to know if reset is available in RACE and did it just occur.
        boolean isDriving              = isME() && m_SIMPlugin.getIODriver().getVars().getBoolean("IsOnTrack");   //This should be set when you are in the car and isME() is true.
        
        m_sessionFlags= m_SIMPlugin.getIODriver().getVars().getBitfield("SessionFlags");
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
                SessionFlags.greenHeld      |
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
        
        m_sessionTime = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTime");

//moved this to _initialize()
//        int sessionNum = m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum");
//        //cache the session type so we aren't parsing in the Session String during these updates every time.
//        if (m_sessionTypes.containsKey(sessionNum)) {
//        	m_sessionType = m_sessionTypes.get(sessionNum);
//        }
//        else {
//        	m_sessionType = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(sessionNum),"SessionType").toUpperCase();
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
//                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverPitTrkPct");
//                if (!s.isEmpty())
//                    this.m_lapCompletedPercent = Double.parseDouble(s);
//            }
            Data pitLocation      = m_SIMPlugin.getSession().getCar(Session.CarIdentifiers.REFERENCE).getPitLocation();
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
        
        String teams = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TeamRacing");
        if (!teams.isEmpty() && Integer.parseInt(teams) != 0) {
        }

        //In team events, this can change, but the caridx and car number will stay the same
        //So, keep it updated in real-time.
        m_driverName = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserName");
        
//      "RadioTransmitCarIdx": -1,
//      "RadioTransmitFrequencyIdx": 5,
//      "RadioTransmitRadioIdx": 0,
        if (!isME()) {
            String s = m_SIMPlugin.getIODriver().getVars().getString("RadioTransmitRadioIdx");
            if (!s.isEmpty()) {
                m_lastKnownRadio = Integer.parseInt(s);
                s = m_SIMPlugin.getIODriver().getVars().getString("RadioTransmitCarIdx");
                if (!s.isEmpty() && Integer.parseInt(s) == m_id) {
                    s = m_SIMPlugin.getIODriver().getVars().getString("RadioTransmitFrequencyIdx");
                    if (!s.isEmpty())
                        m_lastKnownFrequency = Integer.parseInt(s);
                }
            }
        }
        
        if (m_displayUnits != displayUnits) {
            this._setupDefaultUOM(displayUnits);
            m_displayUnits = displayUnits;
        }
        
        if (isME()) {
            fuelLevel = m_SIMPlugin.getIODriver().getVars().getDouble("FuelLevel");
//            if (m_pitLocation < 0.0) {
                String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverPitTrkPct");
                if (!s.isEmpty())
                    m_pitLocation = Double.parseDouble(s);
//            }
            //see below for setting the pit location for other cars when they eventually stop in their pit stall. 
        }

        int trackSurface = TrackSurface.getTrackSurface(m_SIMPlugin.getIODriver(),m_id,isME());
        surfacelocation.setState(trackSurface,m_sessionTime);
        
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
        else
        if (isME() && m_SIMPlugin.getIODriver().getVars().getBoolean("IsInGarage") /*&& m_lapCompletedPercent == -1.0*/) {
            nextStatus.setState(iRacingCar.Status.INGARAGE,m_sessionTime);
        }

        //iRacing has a bug where it outputs lap 2 at the start of the race while the green flag is out
        //for about the first 10% of the race. It is really lap one
        int    currentLap              = (isValid() ? m_SIMPlugin.getIODriver().getVars().getInteger("CarIdxLap", m_id) : -1);
        
        //if we can't get a currentLap from the array, fall back to the none array for IBT files.
        if (currentLap == -1 && isME() && m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLap") == null)
            currentLap = m_SIMPlugin.getIODriver().getVars().getInteger("Lap");
        
        if (currentLap == 2 && (m_sessionFlags & SessionFlags.green) != 0)
            currentLap = 1;

        if (!m_surfacelocation.equals(surfacelocation)) {
            if (Server.logger().getLevel().intValue() >= Level.FINE.intValue())
            Server.logger().fine(String.format("#%-3s (id=%-2d) surfacelocation changed on Lap(%-3.3f) from (%-17s) to (%-17s), DataVersion=(%s)",
                m_number,m_id,
                (double)currentLap + this.m_lapCompletedPercent,
                TrackSurface.toString(m_surfacelocation.getState()),
                TrackSurface.toString(surfacelocation.getState()),
                m_SIMPlugin.getSession().getDataVersion().getString() //getIODriver().getHeader().getLatest_VarBufTick()
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
                //TODO: don't let it change to INPITSALL until we know they are stopped. isME().speed can be used, but other cars?
                //for now just use a delay so ME and other cars will be consistent.
                if (m_prevStatus.getTime(iRacingCar.Status.ENTERINGPITSTALL,m_sessionTime) < ENTER_PIT_DELAY
                &&  !m_prevStatus.equalsPrevious(iRacingCar.Status.INVALID)
                ) {
                    //keep them in the Entering state until they are stopped.
                    nextStatus.setState(iRacingCar.Status.ENTERINGPITSTALL,m_sessionTime);
                }
                else {
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
                isNewCar = true;
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
               //TODO: or over a certain speed, indicating they are leaving the stall. isME().speed can be used, but other cars?
            ) {
                nextStatus.setState(iRacingCar.Status.EXITINGPITSTALL,m_sessionTime);
            }
        }

        //so if we just left the pits and we haven't reached the merge point
        //yet iRacing says we're on the track, set the next status back to leaving pits.
        //This will help the track map logic keep the car on the apron or access road longer.
        //The merge point reference is our position when we left pit road. Tracks with multiple pit roads will have multiple merge points.
        m_mergePoint = m_SIMPlugin.getSession().getTrack().getMergePoint(m_mergePointReference * 100.0) / 100.0;

//if (isME()) {
//    if (m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
//    &&  (nextStatus.equals(iRacingCar.Status.ONTRACK) || nextStatus.equals(iRacingCar.Status.OFFTRACK))
//    ) {
//        if (this.m_lapCompletedPercent > m_mergePoint)
//            m_mergePoint=m_mergePoint;
//    }
//}

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
        }

        //if the if the user is reseting pit box, then we have to wait until they are done before we can
        //send our pit commands or they won't stick
        if (m_SIMPlugin.getIODriver().getAutoResetPitBox() == 1
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
                m_SIMPlugin.getSession().getDataVersion().getString() //getIODriver().getHeader().getLatest_VarBufTick()
            ));
        }

        //exit if we can't get a reading of where we are on the current lap
        if (lapCompletedPercent == -1.0 && nextStatus.equals(Car.Status.INVALID)) {
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
        if (currentLap == -1 && nextStatus.equals(Car.Status.INVALID)) {                 

            //If in the pit stall, wait at least 1 second before allowing the state to change to invalid
            //This will allow the car to have a short blink and not affect the time in the stall
            //This will prevent blinkers from distorting their pit time. 
            //If really leaving, you will stay invalid for more than a second
            if (m_prevStatus.equals(Car.Status.INPITSTALL) 
            &&  m_prevStatus.getTime(Car.Status.INPITSTALL, m_sessionTime) > INVALID_INPITSTALL
            ) {
                m_prevStatus.setState(nextStatus);
            }
            
            return false;
        }

        _gaugeUpdateCurrentLap(currentLap);

        //If lapCompletedPercent is > 1.0, the current lap doesn't, so we increment it
        //It appears there is a delay of these 2 variables and they are not in sync.
        //Also, the low level driver is not normalizing the pct, so we would have to change that also.
        if (lapCompletedPercent >= 1.0) {
            currentLap++;
            lapCompletedPercent -= 1.0;
        }

        //I've seen this be negative, but when it is, the current lap has not incremented
        //so, adjust it to be positive.
        if (lapCompletedPercent < 0.0 && lapCompletedPercent > -1.0)
            lapCompletedPercent += 1.0;

//if (isME()) //just to have a place to put a breakpoint
//    m_lapCompletedPercent = lapCompletedPercent;
//else
       m_lapCompletedPercent = lapCompletedPercent;

       //help out our speed reader by sending this data to it every tick
       m_speedReader.onDataVersionChange(m_sessionTime, m_lapCompletedPercent, m_trackLength.getDouble());
       
        if (fuelLevel >= 0.0)  //if fuel level is good
            m_fuelLevel=fuelLevel;  //use it, else keep last know good one

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
            m_invalidLaps.add(false); //if we just got into the session, mark the early laps invalid.
        if (m_invalidLaps.size() < currentLap)
            m_invalidLaps.add(false);

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
            m_timeRemainingAtStartFinish = m_SIMPlugin.getIODriver().getVars().getDouble("SessionTimeRemain");
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
            || (m_sessionFlags & (
                    SessionFlags.caution
                   |SessionFlags.cautionWaving
                   |SessionFlags.red
                   |SessionFlags.green)
                ) != 0
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

        if (isME() && isNewCar) {
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
                        fuelLevel,prevFuelLevel,
                        m_speedReader.getDouble(),
                        m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()));
//            }
        }

        //did we just enter the session?
        //Entered PITSTALL not from PITROAD in a Non-RACE session
        //Was not blinking
        //TODO: detect RESET activated, Hosted or CarbCup. build_2014_10_21 introduced ability to go to pits on practice without resetting.
        if (!nextStatus.equals(iRacingCar.Status.INVALID)
        && (   isNewCar
            || !m_initialReset
            || (  //entered pit stall from INVALID, means reset from somewhere on the track
                   (nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL) || nextStatus.equals(iRacingCar.Status.INPITSTALL))
                && (m_prevStatus.equals(iRacingCar.Status.INVALID) || m_prevStatus.equals(iRacingCar.Status.ONTRACK) || m_prevStatus.equals(iRacingCar.Status.OFFTRACK) || m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS))
                && !m_sessionType.equalsIgnoreCase("RACE")
               )
            || (  //entered track from INVALID, means start of the race on Track
                   nextStatus.equals(iRacingCar.Status.ONTRACK)
                && m_prevStatus.equals(iRacingCar.Status.INVALID)
                && m_SIMPlugin.getIODriver().getAutoResetPitBox() == 1
                && m_sessionType.equalsIgnoreCase("RACE")
               )
            || (  //entered pit road from INVALID, means start of the race in the pits
                   nextStatus.equals(iRacingCar.Status.ONPITROAD)
                && m_prevStatus.equals(iRacingCar.Status.INVALID)
                && m_SIMPlugin.getIODriver().getAutoResetPitBox() == 1
                && m_sessionType.equalsIgnoreCase("RACE")
               )
            || (  //entered pits from PITROAD, but now has more fuel. Reset on PITROAD while not in a Race
                   nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL)
                && m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
                && fuelLevel > prevFuelLevel
                && !m_sessionType.equalsIgnoreCase("RACE")
               )
           )
        ) {
            //just entered pits from the outside world, what do we want to do? Reset?
            if (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Reset detected from(%s) to (%s) during (%s) autoResetPitBox(%d), autoResetFastRepair(%d), calling _setupReset(Lap=%d), VarBufTick=%d",
                    m_number, m_id,
                    m_prevStatus.getState(),
                    nextStatus.getState(),
                    m_sessionType,
                    m_SIMPlugin.getIODriver().getAutoResetPitBox(),
                    m_SIMPlugin.getIODriver().getAutoResetFastRepair(),
                    m_lapPitted,
                    m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()));

            _setupReset(m_lapPitted, m_SIMPlugin.getIODriver().getAutoResetPitBox(),m_SIMPlugin.getIODriver().getAutoResetFastRepair());

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

        //if we just entered put road
        if (nextStatus.equals(iRacingCar.Status.ONPITROAD)
        && !m_prevStatus.equals(iRacingCar.Status.ONPITROAD)
        ) {
//For now, let's not reissue the commands upon entering pit road
//With the 2015 december build, we not get all the values.            
            if (!isReset && !m_SIMPlugin.getIODriver().build_january_6_2016())
                m_forceSetupCommands = true;
        }

        //if we just entered the pit stall
        if ((nextStatus.equals(iRacingCar.Status.ENTERINGPITSTALL) && !m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL))
        || isNewCar
        ) {
            if (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Entering Pit Stall from(%s) during(%s) speed(%f), calling _setupBeforePitting(Lap=%d), VarBufTick=%d",
                    m_number,m_id,
                    m_prevStatus.getState(),
                    m_sessionType,
                    m_speedReader.getDouble(),
                    m_lapPitted,
                    m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
                ));

            _setupBeforePitting(m_lapPitted);
        }

        //is the car in the pit stall
        if (nextStatus.equals(iRacingCar.Status.INPITSTALL)) {

            //see if the location of the pit stall has not been set and set it.
            if (m_pitLocation < 0.0) {
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

                //here we want to keep the repair time because iRacing zero's it out when you leave the pits
                //even if you didn't complete the repairs
                double repairtime    = m_SIMPlugin.getIODriver().getVars().getDouble("PitRepairLeft");
                if (repairtime > 0.01 || isReset || m_speedReader.getDouble() < .01)
                    m_repairTime = repairtime;
                double repairtimeopt = m_SIMPlugin.getIODriver().getVars().getDouble("PitOptRepairLeft");
                if (repairtimeopt > 0.01 || isReset || m_speedReader.getDouble() < .01)
                    m_repairTimeOptional = repairtimeopt;

                //Since iRacing doesn't support sending commands for the Tape,Wedge and BrakeBias
                //we will read the current values as they do get output when you make the changes in their black box

            } //isME()
        } //is the car in the pit stall

        //car is just exited the pit stall
        if (m_stoppedInPitStall
        &&  nextStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
        && !m_prevStatus.equals(iRacingCar.Status.EXITINGPITSTALL)
        && m_speedReader.getDouble() > 0.0 //we're moving and not exiting the car
        ) {
            if (isME())
                Server.logger().info(String.format("#%-3s (id=%d) Exiting Pits during(%s) time(%.1f) prevStatus(%s) status(%s) speed(%f) lap(%d), calling _setupTakeReading(), VarBufTick=%d",
                    m_number,m_id,
                    m_sessionType,
                    m_prevStatus.getTime(iRacingCar.Status.INPITSTALL, m_sessionTime),
                    m_prevStatus.getState(),
                    nextStatus.getState(),
                    m_speedReader.getDouble(),
                    m_lapPitted,
                    m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
                ));

            _setupTakeReading();
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
                    m_speedReader.getDouble(),
                    m_lapPitted,
                    m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
                ));

            _setupAfterPitting(m_lapPitted);
            m_forceSetupCommands = true;
            m_stoppedInPitStall = false;
        }
//        else //any time you leave the pits without stopping, iRacing resets the pit flags. We need to change them back
        //if you just entered the track and the autoResetPitBox is on, then call _setupReset() so by default everything is checked to be changed next stop
        if (/*!m_stoppedInPitStall
        &&*/ m_SIMPlugin.getIODriver().getAutoResetPitBox() == 1
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
                    m_SIMPlugin.getIODriver().getAutoResetFastRepair(),
                    m_prevStatus.getState(),
                    nextStatus.getState(),
                    m_sessionType,
                    m_lapPitted,
                    m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()));

            _setupReset(m_lapPitted,m_SIMPlugin.getIODriver().getAutoResetPitBox(),m_SIMPlugin.getIODriver().getAutoResetFastRepair());

//as of the March 30, 2016 patch, this option is now available with app.ini[Pit Service]autoResetFastRepair            
//            //turn off fast repairs even if user has auto reset = 1
//            if (Server.getArg("gauge-fastrepairs-autoreset-off",true))
//                this._getGauge(Gauge.Type.FASTREPAIRS).setChangeFlag(false);
            
            
            //m_forceSetupCommands = true;
        }
        else //any time you over shoot your pit without stopping and backup to pit road, iRacing resets the pit flags. We need to change them back
        if (!m_stoppedInPitStall
        &&   m_SIMPlugin.getIODriver().getAutoResetPitBox() == 1
        &&   nextStatus.equals(iRacingCar.Status.ONPITROAD)
        &&   (  m_prevStatus.equals(iRacingCar.Status.LEAVINGPITS)
             )
        ) {
            m_forceSetupCommands = true;
        }

        m_prevStatus.setState(nextStatus);

        //update the pit times array
        while (m_pitTimes.size() < m_lapCompleted || m_pitTimes.size() < m_lapPitted)
            m_pitTimes.add(0.0);
            
        m_pitTimes.set(m_lapPitted-1, m_prevStatus.getTime(iRacingCar.Status.INPITSTALL,m_sessionTime) );
        
        //In the Sept 2015 build, flags and amounts in the pit black boxes were added.
        //This code updates the gauges with that information, but only if we didn't just send the 
        //commands to the SIM.
        
        if (_sendSetupCommands()) {
            m_resetTime = m_sessionTime;
            _setupBeforePitting(m_lapPitted);
        }
        
        //Now copy the states from the gear specific tach to the main tach based on the gear the car is in
        String gear  = this._getGauge(Gauge.Type.GEAR).getValueCurrent().getString();
        String power = String.format("%.0f",this._getGauge(Gauge.Type.ENGINEPOWER).getValueCurrent().getDouble());
        if (!m_gear.equals(gear) || !m_power.equals(power)) {
            
            //see if a tach gauge exists for the gear we are in
            String gaugeName = String.format("%s-%s-%s", Gauge.Type.TACHOMETER, gear, power);
            if (m_gauges.containsKey(gaugeName.toLowerCase())) {
                Gauge tachByGearByPower = this._getGauge(gaugeName);
                Gauge tach = this._getGauge(Gauge.Type.TACHOMETER);
                tach.addStateRange(tachByGearByPower);
            }
            else {
                gaugeName = String.format("%s-%s", Gauge.Type.TACHOMETER, gear);
                if (m_gauges.containsKey(gaugeName.toLowerCase())) {
                    Gauge tachByGear        = this._getGauge(gaugeName);
                    Gauge tach = this._getGauge(Gauge.Type.TACHOMETER);
                    tach.addStateRange(tachByGear);
                }
            }
            m_gear = gear;
            m_power = power;
        }

//TODO: redesign the Gauge class so it has multiple readers for real-time, next value on pit, after pit        
        if (isME() 
        &&  !isReset
        && (m_sessionTime - 2.0 > m_resetTime) //give the broadcast commands time to settle
//        && false
        && m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitSvFlags") != null
        ) {
            int pitFlags  = m_SIMPlugin.getIODriver().getVars().getBitfield("PitSvFlags");

            Gauge  gauge;
            String UOM;
            double value;
            int    flag;

            for (String tire : Car.Tires) {
                gauge = this._getGauge("TIREPRESSURE"+tire);
                UOM   = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitSv"+tire+"P").Unit;
                value = m_SIMPlugin.getIODriver().getVars().getDouble("PitSv"+tire+"P");
                if (m_SIMPlugin.getIODriver().build_december_7_2015()) {
                    boolean changeFlag = gauge.getChangeFlag().getBoolean();
                    gauge.setValueNext(value, UOM);
                    gauge.setChangeFlag(changeFlag);
                    gauge._setIsSentToSIM(true);
                }
                flag  = PitSvFlags.getFlag(tire);

                //if (!gauge.getChangeFlag().equals((pitFlags & flag) != 0)) {
                    if ((pitFlags & flag) == 0) {
                        //in here the flag is being reset.
                        //if we're in the pits, then tell the gauge to take the after pit readings.
                        if (gauge.getChangeFlag().getBoolean()) {
                            if (m_prevStatus.equals(iRacingCar.Status.INPITSTALL) || m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL) ) {
                                gauge.takeReading();
                                gauge.afterPitting(m_lapPitted);
                            }
                            gauge.setChangeFlag(false);
                        }
                    }
                    else {
                        if (!gauge.getChangeFlag().getBoolean()) {
                            gauge.setChangeFlag(true);
                        
                            //if we're in the pitstall and the flag is being turned on
                            //take a before reading
                            if (m_prevStatus.equals(iRacingCar.Status.INPITSTALL) || m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL) ) {
                                gauge.beforePitting(m_lapPitted);
                            }
                        }                        
                    }
                    gauge._setIsSentToSIM(true);
                //}
            }
            
            gauge = this._getGauge(Gauge.Type.FUELLEVEL);
            UOM   = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("PitSvFuel").Unit;
            value = m_SIMPlugin.getIODriver().getVars().getDouble("PitSvFuel");
            flag  = PitSvFlags.FuelFill;
            if (UOM.equals("kg")) { //if in weight, use conversion to liters provided. There's no standard conversion for this.
                try {
                    double kgPerLiter = Double.parseDouble(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarFuelKgPerLtr"));
                    value = value / kgPerLiter;
                    UOM = "l";
                }
                catch (NumberFormatException e) {}
            }
            
            if (!gauge.getChangeFlag().equals((pitFlags & flag) != 0)
            || value != gauge.getValueNext(UOM).getDouble()) {
                if ((pitFlags & flag) == 0 
                && (m_prevStatus.equals(iRacingCar.Status.INPITSTALL) || m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL))
                ) {
                    //in here the flag is being reset.
                    //if we're in the pits, then tell the gauge to take the after pit readings.
                    gauge.takeReading();
                    gauge.afterPitting(m_lapPitted);
                }
                else {
                    gauge.setValueNext(value, UOM);
                    gauge.setChangeFlag((pitFlags & flag) != 0);
                }
                gauge._setIsSentToSIM(true);
            }
            
            gauge = this._getGauge(Gauge.Type.WINDSHIELDTEAROFF);
            flag  = PitSvFlags.WindshieldTearoff;

            if (!gauge.getChangeFlag().equals((pitFlags & flag) != 0)) {
                if ((pitFlags & flag) == 0 
                && (m_prevStatus.equals(iRacingCar.Status.INPITSTALL) || m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL))
                ) {
                    //in here the flag is being reset.
                    //if we're in the pits, then tell the gauge to take the after pit readings.
                    gauge.takeReading();
                    gauge.afterPitting(m_lapPitted);
                }
                else {
                    gauge.setChangeFlag((pitFlags & flag) != 0);
                }
                gauge._setIsSentToSIM(true);
            }
            
            gauge = this._getGauge(Gauge.Type.FASTREPAIRS);
            flag  = PitSvFlags.FastRepair;
            
            if (!gauge.getChangeFlag().equals((pitFlags & flag) != 0)) {
                if ((pitFlags & flag) == 0 
                && (m_prevStatus.equals(iRacingCar.Status.INPITSTALL) || m_prevStatus.equals(iRacingCar.Status.ENTERINGPITSTALL))
                ) {
                    //in here the flag is being reset.
                    //if we're in the pits, then tell the gauge to take the after pit readings.
                    gauge.takeReading();
                    gauge.afterPitting(m_lapPitted);
                }
                else {
                    gauge.setChangeFlag((pitFlags & flag) != 0);
                }
                gauge._setIsSentToSIM(true);
            }
        }
        
        return isValid();
    }

    @SuppressWarnings("unchecked")
    private void _initialize() {

        //See if the session is connected and pumping data
        if (m_SIMPlugin.getIODriver().getVarHeaders() == null
        ||  m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("SessionNum") == null
        ||  m_SIMPlugin.getIODriver().getSessionInfo() == null
        ||  m_SIMPlugin.getIODriver().getSessionInfo().getData() == null
        ||  ((Map<String,Map<String,Integer>>)m_SIMPlugin.getIODriver().getSessionInfo().getData()).get("DriverInfo") == null
        )
            return;

        int sessionNum = m_SIMPlugin.getIODriver().getVars().getInteger("SessionNum");

        //cache the session type so we aren't parsing in the Session String during these updates every time.
        m_sessionType = m_SIMPlugin.getIODriver().getSessionInfo().getString("SessionInfo","Sessions",Integer.toString(sessionNum),"SessionType").toUpperCase();

//        m_trackName  = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackName");
        m_trackType  = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackType");

        {
            String s[]   = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackPitSpeedLimit").split(" ");
            if (s.length == 2) {
                m_trackSpeedLimit = new Data("TrackPitSpeedLimit",Double.parseDouble(s[0]),s[1],Data.State.NORMAL);
            }
        }

        {
            String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackLength").split(" ");
            if (s.length == 2) {
                m_trackLength = new Data("TrackLength",Double.parseDouble(s[0]),s[1],Data.State.NORMAL);
            }
        }

        //Save the index to me
        m_ME = ((Map<String,Map<String,Integer>>)m_SIMPlugin.getIODriver().getSessionInfo().getData()).get("DriverInfo").get("DriverCarIdx");

        //if the car is not in the session, don't try to initialize it.
        if (m_id == -1)
            return;

        //These values are used a lot, so go ahead and cache them
        m_number     = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumber");
        String numberRaw  = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarNumberRaw");
        if (!numberRaw.isEmpty())
            m_numberRaw = Integer.parseInt(numberRaw);
        m_driverName = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"UserName");

        //get the lap completed from the results, then we will use current lap later to keep this updated.
        m_lapCompleted = m_results.getLapCompleted();

        //Now apply all the state ranges from iRacing before we load the json file.
        //The RPM settings really only apply to ME, but in single car sessions, it can apply to all
        //and since we can get RPMs for any car, i will set them for every car.
        //TODO: How to detect multicar and only set these for the same car as ME?
        try {
            Gauge gauge = _getGauge(Gauge.Type.TACHOMETER);

            //Update the Critical range with the Shift RPM for this car
//            DriverCarRedLine: 10100.000
//            DriverCarSLFirstRPM: 8500.000
//            DriverCarSLShiftRPM: 9500.000
//            DriverCarSLLastRPM: 9500.000
//            DriverCarSLBlinkRPM: 9800.000

            //TODO: In a multiclass session, how to I get the RPM marks for each class?
            double DriverCarSLShiftRPM = Double.parseDouble(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLShiftRPM"));
            double DriverCarRedLine    = Double.parseDouble(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarRedLine"));
            double DriverCarSLFirstRPM = Double.parseDouble(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLFirstRPM"));
            double DriverCarSLLastRPM  = Double.parseDouble(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLLastRPM"));
            double DriverCarSLBlinkRPM = Double.parseDouble(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarSLBlinkRPM"));
//DriverCarSLShiftRPM = 9000.0;
            gauge.addStateRange("SHIFTLIGHTS",            DriverCarSLFirstRPM,                  DriverCarSLShiftRPM);
            gauge.addStateRange("SHIFT",                  DriverCarSLShiftRPM,                  DriverCarSLBlinkRPM);
            gauge.addStateRange("SHIFTBLINK",             DriverCarSLBlinkRPM,                  DriverCarRedLine);
            gauge.addStateRange(Data.State.CRITICAL,      DriverCarRedLine,                     Double.MAX_VALUE);

            Server.logger().fine(String.format("iRacingCar._initialize() returned First=%.0f, Shift=%.0f, Last=%.0f, Blink=%.0f, RedLine=%.0f for #%s(%d) - %s",
                    DriverCarSLFirstRPM,
                    DriverCarSLShiftRPM,
                    DriverCarSLLastRPM,
                    DriverCarSLBlinkRPM,
                    DriverCarRedLine,
                    m_number,m_id,m_name));
        }
        catch (NumberFormatException e) {}

        Map<String,Object> configMap = this._loadCar("com/SIMRacingApps/SIMPlugins/iRacing/Cars/"+m_name.replace(" ", "_")+".json");

        if (configMap.containsKey("Description"))
            m_description = (String)configMap.get("Description");

        //if the derived class did not override this, use the car short name from iRacing
        if (m_description.equals(m_name)) {
            m_description = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassShortName");
        }

        //apply overrides to the gauge where iRacing gives me the values
        //adjust the max capacity of the fuel gauge if the session has limited fuel for this class of cars

        //The iRacing will let me know when the Water Temps are at the WARNING level,
        //using EngineWarnings.waterTempWarning, so remove the state.
        _getGauge(Gauge.Type.WATERTEMP).removeStateRange("WARNING");
        
        //In the next build, after July 2015, David removed CarClassMaxFuel and replaced it with DriverCarFuelMaxLtr.
        //Currently, CarClassMaxFuel contains the percentage of fuel to use in this session
        
        String maxfuel         = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarFuelMaxLtr"); //TODO: should ask David why max fuel not in Drivers per car class?
        String maxfuelpct      = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassMaxFuelPct");
        Gauge  fuelgauge       = _getGauge(Gauge.Type.FUELLEVEL);
        Data   capacityMaximum = fuelgauge.getCapacityMaximum("l");
        double capacityPercent = 1.0;
        
        if (maxfuel.isEmpty()) //for older builds get the percentage out of CarClassMaxFuel
            maxfuel = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",m_driversIdx.toString(),"CarClassMaxFuel");
        else
            maxfuel += " l"; //this is in liters with no UOM in the data
        
        if (!maxfuel.isEmpty()) {
            String s[] = maxfuel.split("[ ]");
            if (s.length == 2) {
                if (s[1].equals("%")) {
                    capacityPercent = Double.parseDouble(s[0]);
                }
                else {
                    //this assumes, if not a percentage, David could get the max fuel for each car.
                    capacityMaximum = (new Data("",Double.parseDouble(s[0]),s[1])).convertUOM("l");
                }
            }
        }
        
        //if this not null, the we are on the new build, use it.
        if (!maxfuelpct.isEmpty()) {
            String s[] = maxfuelpct.split("[ ]");
            if (s.length == 2) {
                capacityPercent = Double.parseDouble(s[0]);
            }
        }
        
        fuelgauge.setCapacityMaximum( capacityMaximum.getDouble() * capacityPercent, "l" );

        m_speedReader = new VarDataDoubleSpeed(m_SIMPlugin.getIODriver(),this);
        
        if (isME()) {
            //get the Tape reader. Each car can specify which reader to use. iRacing used to return only 0 or 1 for with and without tape
            //new versions of the build did different things on different cars. Refer to each reader class for specifics.
            VarDataDouble tapeReader = new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpQtape",    "%");
            Map<String,Map<String,Object>> gauges = (Map<String, Map<String, Object>>) configMap.get("Gauges");
            if (gauges != null) {
                Map<String,Object> gauge = (Map<String,Object>)gauges.get("Tape");
                if (gauge != null) {
                    Map<String,Object> track = (Map<String, Object>) gauge.get("default");
                    if (track != null) {
                        String reader = (String)track.get("Reader");
                        if (reader != null && (reader.equals("DataVarTape") || reader.equals("com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar$DataVarTape")))
                            tapeReader = new VarDataDoubleTape(m_SIMPlugin.getIODriver(),this);
                        else
                        if (reader != null && (reader.equals("DataVarTapePct") || reader.equals("com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar$DataVarTapePct")))
                            tapeReader = new VarDataDoubleTapePct(m_SIMPlugin.getIODriver(),this);
                        else
                        if (reader != null && (reader.equals("DataVarTape4") || reader.equals("com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar$DataVarTape4")))
                            tapeReader = new VarDataDoubleTape4(m_SIMPlugin.getIODriver(),this);
                    }
                }
            }

            //create a global fuel reader so we can add the conversion to weight
            //All methods that return a fuel Data object should copy the conversions from this
            m_fuelReader = new VarDataDouble(m_SIMPlugin.getIODriver(),this,"FuelLevel",  "l");
            double kgPerLiter = Double.parseDouble(m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","DriverCarFuelKgPerLtr"));
            m_fuelReader.addConversion("L", "KG", kgPerLiter);
            m_fuelReader.addConversion("L", "LB", new Data("",kgPerLiter,"KG").convertUOM("LB").getDouble());
            
            //Create readers for all the gauges iRacing can provide values for
            //The UOM is used if the headers don't have it set. In some releases they were not set, so these are the documented UOMs
            this._getGauge(Gauge.Type.SPEEDOMETER)        .setSIMValue(m_speedReader,                                                             Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.TACHOMETER)         .setSIMValue(new VarDataDoubleRPM(m_SIMPlugin.getIODriver(),this),                      Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.GEAR)               .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"Gear",       ""),        Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.WATERLEVEL)         .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"WaterLevel", "l"),       Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.WATERPRESSURE)      .setSIMValue(new VarDataDoubleWaterPressure(m_SIMPlugin.getIODriver(),this),            Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.WATERTEMP)          .setSIMValue(new VarDataDoubleWaterTemp(m_SIMPlugin.getIODriver(),this),                Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.FUELLEVEL)          .setSIMValue(m_fuelReader,                                                                Gauge.SIMValueTypes.ForCarZeroOnPit);
            this._getGauge(Gauge.Type.FUELPRESSURE)       .setSIMValue(new VarDataDoubleFuelPress(m_SIMPlugin.getIODriver(),this),                Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.OILLEVEL)           .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"OilLevel",   "l"),       Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.OILPRESSURE)        .setSIMValue(new VarDataDoubleOilPress(m_SIMPlugin.getIODriver(),this),                 Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.OILTEMP)            .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"OilTemp",    "C"),       Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.VOLTAGE)            .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"Voltage",    "v"),       Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.ABS)                .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcABS",""),              Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.BRAKEBIASADJUSTMENT).setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcBrakeBias","%"),       Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.TRACTIONCONTROL)    .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcTractionControl",""),  Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.TAPE)               .setSIMValue(tapeReader,                                                                Gauge.SIMValueTypes.ForSetup);
            this._getGauge(Gauge.Type.WINDSHIELDTEAROFF)  .setSIMValue(new VarDataDoubleWindshieldTearoff(m_SIMPlugin.getIODriver(),this),        Gauge.SIMValueTypes.ZeroOnPit);
            this._getGauge(Gauge.Type.FASTREPAIRS)        .setSIMValue(new VarDataDoubleFastRepairs(m_SIMPlugin.getIODriver(),this),              Gauge.SIMValueTypes.ForCarAndSetup);

            //check to see if the new value exists, otherwise use old value for recorded files.
            if (m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRrWedgeAdj") != null)
                this._getGauge(Gauge.Type.RRWEDGEADJUSTMENT)  .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpRrWedgeAdj", "mm"),Gauge.SIMValueTypes.ForSetup);
            else
                this._getGauge(Gauge.Type.RRWEDGEADJUSTMENT)  .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpWedgeAdj", "mm"),  Gauge.SIMValueTypes.ForSetup);

            this._getGauge(Gauge.Type.LRWEDGEADJUSTMENT)  .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpLrWedgeAdj", "mm"),    Gauge.SIMValueTypes.ForSetup);
            //this._getGauge(Gauge.Type.RRPERCHOFFSET)      .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpRrPerchOffsetm", "mm"),Gauge.SIMValueTypes.ForSetup);

            if (m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRWingAngle") != null) {
                this._getGauge(Gauge.Type.FRONTWING)          .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpFWingAngle", "deg"),   Gauge.SIMValueTypes.ForSetup);
                this._getGauge(Gauge.Type.REARWING)           .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpRWingAngle", "deg"),   Gauge.SIMValueTypes.ForSetup);
            }
            else
            if (m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("dpRWingSetting") != null) {
                this._getGauge(Gauge.Type.FRONTWING)          .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpFWingSetting", ""),   Gauge.SIMValueTypes.ForSetup);
                this._getGauge(Gauge.Type.REARWING)           .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpRWingSetting", ""),   Gauge.SIMValueTypes.ForSetup);
            }
            else {
                this._getGauge(Gauge.Type.FRONTWING)          .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpFWingIndex", "deg"),   Gauge.SIMValueTypes.ForSetup);
                this._getGauge(Gauge.Type.REARWING)           .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpRWingIndex", "deg"),   Gauge.SIMValueTypes.ForSetup);
            }
            
            this._getGauge(Gauge.Type.ANTIROLLREAR)       .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcAntiRollRear", ""),    Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.ANTIROLLFRONT)      .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcAntiRollFront", ""),   Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.POWERSTEERINGASSIST).setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpPSSetting", ""),       Gauge.SIMValueTypes.ForSetup);
            this._getGauge(Gauge.Type.FRONTFLAP)          .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dpFNOMKnobSetting", ""), Gauge.SIMValueTypes.ForSetup);
            
            this._getGauge(Gauge.Type.FUELMIXTURE)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcFuelMixture", ""),     Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.THROTTLESHAPE)      .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcThrottleShape", ""),   Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.ENGINEPOWER)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcEnginePower", ""),     Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.ENGINEBRAKING)      .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcEngineBraking", ""),   Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.DIFFENTRY)          .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcDiffEntry", ""),       Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.DIFFMIDDLE)         .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcDiffMiddle", ""),      Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.DIFFEXIT)           .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcDiffExit", ""),        Gauge.SIMValueTypes.ForCarAndSetup);

            this._getGauge(Gauge.Type.WEIGHTJACKERLEFT)   .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcWeightJackerLeft", ""),Gauge.SIMValueTypes.ForCarAndSetup);
            this._getGauge(Gauge.Type.WEIGHTJACKERRIGHT)  .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"dcWeightJackerRight", ""),Gauge.SIMValueTypes.ForCarAndSetup);
            
            this._getGauge(Gauge.Type.BRAKE)              .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"Brake",      "%"),       Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.BRAKEPRESSURE)      .setSIMValue(new VarDataDoubleBrakePressure(m_SIMPlugin.getIODriver(),this),            Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.THROTTLE)           .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"Throttle",   "%"),       Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.CLUTCH)             .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"Clutch",     "%"),       Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.STEERING)           .setSIMValue(new VarDataDoubleSteer(m_SIMPlugin.getIODriver(),this),                    Gauge.SIMValueTypes.ForCar);

            //Tire Pressures
            this._getGauge(Gauge.Type.TIREPRESSURELF)     .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LFcoldPressure","kPa"), Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.TIREPRESSURELR)     .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LRcoldPressure","kPa"), Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.TIREPRESSURERF)     .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RFcoldPressure","kPa"), Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.TIREPRESSURERR)     .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RRcoldPressure","kPa"), Gauge.SIMValueTypes.ForCar);

            //Tire Temps
            this._getGauge(Gauge.Type.TIRETEMPLFL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LFtempCL","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPLFM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LFtempCM","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPLFR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LFtempCR","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPLRL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LRtempCL","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPLRM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LRtempCM","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPLRR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LRtempCR","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPRFL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RFtempCL","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPRFM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RFtempCM","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPRFR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RFtempCR","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPRRL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RRtempCL","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPRRM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RRtempCM","C"),         Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIRETEMPRRR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RRtempCR","C"),         Gauge.SIMValueTypes.AfterPit);

            //Tire Wear
            this._getGauge(Gauge.Type.TIREWEARLFL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LFwearL","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARLFM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LFwearM","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARLFR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LFwearR","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARLRL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LRwearL","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARLRM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LRwearM","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARLRR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"LRwearR","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARRFL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RFwearL","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARRFM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RFwearM","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARRFR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RFwearR","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARRRL)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RRwearL","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARRRM)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RRwearM","%"),          Gauge.SIMValueTypes.AfterPit);
            this._getGauge(Gauge.Type.TIREWEARRRR)        .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"RRwearR","%"),          Gauge.SIMValueTypes.AfterPit);
            
//this._getGauge(Gauge.Type.TACHOMETER)         .setIsDebug(true);
//this._getGauge(Gauge.Type.FUELLEVEL)          .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREPRESSURELF)     .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREPRESSURELR)     .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREPRESSURERF)     .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREPRESSURERR)     .setIsDebug(true);
//this._getGauge(Gauge.Type.TAPE)               .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARLFL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARLFM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARLFR)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARLRL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARLRM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARLRR)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARRFL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARRFM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARRFR)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARRRL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARRRM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIREWEARRRR)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPLFL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPLFM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPLFR)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPLRL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPLRM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPLRR)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPRFL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPRFM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPRFR)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPRRL)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPRRM)        .setIsDebug(true);
//this._getGauge(Gauge.Type.TIRETEMPRRR)        .setIsDebug(true);

        } /* isME() */
        else {
            //put here how to get values of the other cars. iRacing does not output much info for other cars
            this._getGauge(Gauge.Type.TACHOMETER)         .setSIMValue(new VarDataDoubleRPM(m_SIMPlugin.getIODriver(),this,m_id),                        Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.GEAR)               .setSIMValue(new VarDataDouble(m_SIMPlugin.getIODriver(),this,"CarIdxGear",m_id,""),           Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.STEERING)           .setSIMValue(new VarDataDoubleSteer(m_SIMPlugin.getIODriver(),this,m_id),                      Gauge.SIMValueTypes.ForCar);
            this._getGauge(Gauge.Type.SPEEDOMETER)        .setSIMValue(m_speedReader,                                                                    Gauge.SIMValueTypes.ForCar);
        }

//All of these were moved to the json file.        
        //now setup the gauges we can adjust

        //these gauges can always be adjusted, set the isFixed to false;
//        this.getGauge(Gauge.Type.FUELLEVEL)        .setIsFixed(false);
//        this.getGauge(Gauge.Type.GEAR)             .setIsFixed(false);
//        this.getGauge(Gauge.Type.WINDSHIELDTEAROFF).setIsFixed(false);

        //With these gauges set the change flag on reset
//        this.getGauge(Gauge.Type.TIREPRESSURELF)   .setOnResetChange(true);
//        this.getGauge(Gauge.Type.TIREPRESSURELR)   .setOnResetChange(true);
//        this.getGauge(Gauge.Type.TIREPRESSURERF)   .setOnResetChange(true);
//        this.getGauge(Gauge.Type.TIREPRESSURERR)   .setOnResetChange(true);
//        this.getGauge(Gauge.Type.FUELLEVEL)        .setOnResetChange(true);
//        this.getGauge(Gauge.Type.WINDSHIELDTEAROFF).setOnResetChange(true);

        //if iRacing says it's a fixed setup session, then set the isFixed on all gauges we can't change
        String fixed = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","IsFixedSetup");

        try {
            if (Integer.parseInt(fixed) == 1) {
                this._getGauge(Gauge.Type.TIREPRESSURELF)     .setIsFixed(true);
                this._getGauge(Gauge.Type.TIREPRESSURELR)     .setIsFixed(true);
                this._getGauge(Gauge.Type.TIREPRESSURERF)     .setIsFixed(true);
                this._getGauge(Gauge.Type.TIREPRESSURERR)     .setIsFixed(true);
                //TODO: gauges we can't change via send keys (wedge,tape,brake bias), set to false as well
                //NOTE: for now setting these to fixed will simply tell any clients they can't change the setup remotely
                //      Therefore, the client can use this to disable/enable the feature.
                //      It doesn't mean you can't change it through the SIM.
                this._getGauge(Gauge.Type.RRWEDGEADJUSTMENT)  .setIsFixed(true);
                this._getGauge(Gauge.Type.BRAKEBIASADJUSTMENT).setIsFixed(true);
                this._getGauge(Gauge.Type.TAPE)               .setIsFixed(true);
                this._getGauge(Gauge.Type.ABS)                .setIsFixed(true);
                this._getGauge(Gauge.Type.TRACTIONCONTROL)    .setIsFixed(true);
                this._getGauge(Gauge.Type.ANTIROLLFRONT)      .setIsFixed(true);
                this._getGauge(Gauge.Type.ANTIROLLREAR)       .setIsFixed(true);
                this._getGauge(Gauge.Type.FUELMIXTURE)        .setIsFixed(true);
                this._getGauge(Gauge.Type.WEIGHTJACKERLEFT)   .setIsFixed(true);
                this._getGauge(Gauge.Type.WEIGHTJACKERRIGHT)  .setIsFixed(true);
            }
        } catch (NumberFormatException e) {}

//promoted this up to Car.java so all SIMs report the states the same way        
//        {
//            //now set the speedometer states based on pit road speed limit
//            Gauge gauge = this.getGauge(Gauge.Type.SPEEDOMETER);
//
//            //get the speed limit and floor it. Typically, users don't see fraction's in the speed gauges
//            //convert the track UOM to the gauges UOM
//            double PitRoadSpeedLimit = Math.floor(m_trackSpeedLimit.convertUOM(gauge.getUOM().getString()).getDouble());
//
//            double WayOverPitSpeed     = 1.10;
//            double OverPitSpeed        = (PitRoadSpeedLimit + 1.0) / PitRoadSpeedLimit;
//            double PitSpeed            = (PitRoadSpeedLimit - 1.0) / PitRoadSpeedLimit;
//            double ApproachingPitSpeed = PitSpeed - (7*.012) - (7*.006);
//
//            gauge.addState(Data.State.WAYOVERLIMIT,     PitRoadSpeedLimit * WayOverPitSpeed,     Double.MAX_VALUE);
//            gauge.addState(Data.State.OVERLIMIT,        PitRoadSpeedLimit * OverPitSpeed,        PitRoadSpeedLimit * WayOverPitSpeed);
//            gauge.addState(Data.State.LIMIT,            PitRoadSpeedLimit * PitSpeed,            PitRoadSpeedLimit * OverPitSpeed);
//            gauge.addState(Data.State.APPROACHINGLIMIT, PitRoadSpeedLimit * ApproachingPitSpeed, PitRoadSpeedLimit * PitSpeed);
//
//        }
//
//if (isME()) dumpGauges();

    }
}
