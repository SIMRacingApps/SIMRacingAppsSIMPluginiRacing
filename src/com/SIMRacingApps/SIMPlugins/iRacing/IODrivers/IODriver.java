package com.SIMRacingApps.SIMPlugins.iRacing.IODrivers;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.SIMPlugins.iRacing.*;
import com.SIMRacingApps.Util.FindFile;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2022 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class IODriver {
    //TODO: Move the builds to it's own class and pass an instance of it down to SessionInfo
    //functions that will return true if the driver returns a start date after the build date
    //file based drivers could have recordings from older builds and the code should be maintained to read those correctly.
    //See iRacing release notes for documented changes, else I will list the ones I found here otherwise.

    /**
     * 1. dpQtape changed on some cars from 0-10 to 0-100. 
     * @return true if the data is from data recorded after this build
     */
    public boolean build_april_22_2014() {
        Calendar cal = Calendar.getInstance(); cal.set(2014, 3, 22, 0, 0, 0);
        long sessionDate = getHeader().getSubHeader().getSessionStartDate();
        return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }
    /**
     * - Output RadioTransmitRadioIdx and RadioTransmitFrequencyIdx in addition to RadioTransmitCarIdx.
     *   These new indexes point back to ME, not the driver talking in this build.
     * - Output ClassPosition in QualifyResultsInfo section to match ResultsPositions.
     * - Output CarClassColor.
          CarClassColor: 0xffda59
     * - Output car, suit, helmet paint info.
          CarDesignStr: 0,000000,55040d,ffffff,ed2129 (the last value, tire color, is optional and may be missing)
          HelmetDesignStr: 67,270000,55040d,000000
          SuitDesignStr: 14,ffffff,fc0706,68fe69
          CarNumberDesignStr: 0,0,ffffff,777777,000000
          CarSponsor_1: 98
          CarSponsor_2: 107
     * - Properly escape more strings in the YAML session string.
          CarNumber: "61"
          CarNumberRaw: 61
     * - Properly show pressure in telemetry when pressure based brake bias is used.
     * - Fix telemetry for steering angle on remote cars.
     * @return true if the data is from data recorded after this build
     */
    public boolean build_july_21_2014() {
        Calendar cal = Calendar.getInstance(); cal.set(2014, 6, 21, 0, 0, 0);
        long sessionDate = getHeader().getSubHeader().getSessionStartDate();
        return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }

    /**
    * - New session string information:
    * -
    * - TrackConfigName:
    * - TeamRacing:
    * - MinDrivers:
    * - MaxDrivers:
    * - DCRuleSet:
    * - QualifierMustStartRace:
    * - NumCarClasses:
    * - NumCarTypes:
    * - CarNumberRaw:
    * - CarScreenName:
    * - CarScreenNameShort:
    * - CarDesignStr:
    * - CarClassColor:
    * - HelmetDesignStr:
    * - SuitDesignStr:
    * - CarNumberDesignStr:
    * - CarSponsor_1:
    * - CarSponsor_2:
    * - LicString:
    * -
    * - - Added a new IsOnTrackCar variable to augment IsOnTrack. IsOnTrack now indicates that the driver is in the car,
    * -   and IsOnTrackCar indicates that the car is on track, with or without a driver.
    * -
    * - - Properly handle negative numbers in YAML parser.
    * -
    * - - Force a 5 second timeout before starting to log telemetry to disk again, after shutting it off.
    * -   Or if asked to swap files, ignore swap requests for 5 seconds after a successful swap.
    * @return true if the data is from data recorded after this build
    */
    public boolean build_october_21_2014() {
        Calendar cal = Calendar.getInstance(); cal.set(2014, 9, 21, 0, 0, 0);
        long sessionDate = getHeader().getSubHeader().getSessionStartDate();
        return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }

    /**
     * Car Class not updated and is 1 based, not 0.
     * @return true if the data is from data recorded after this build
     */
    public boolean build_november_12_2014() {
         Calendar cal = Calendar.getInstance(); cal.set(2014, 10, 12, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
     }

    /**
     * Car Class changed back to zero based.
     * @return true if the data is from data recorded after this build
     */
    public boolean build_december_9_2014() {
         Calendar cal = Calendar.getInstance(); cal.set(2014, 11, 9, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
     }

    /**
     * - New trucks, Silverado2015 and Tundra2015 with tape percentage
     * - Add in new iRSDK remote message to fast forward replay based on a session number and session time.
     * - Output shock velocity to telemetry.
     * - Fast Repairs can be controlled by user now, but not exported to telemetry or broadcast messages.
     * @return true if the data is from data recorded after this build
     */
    public boolean build_march_11_2015() {
         Calendar cal = Calendar.getInstance(); cal.set(2015, 2, 11, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }

    /**
     *   http://members.iracing.com/jforum/posts/list/1500/1470675.page
     * 
     *  New live telemetry outputs 
     *  CarIdxClassPosition             Cars class position in race by car index,
     *  CarIdxEstTime                   Estimated time to reach current location on track, s
     *  CarIdxF2Time                    Race time behind leader or fastest lap time otherwise, s
     *  CarIdxPosition                  Cars position in race by car index,
     *  DCDriversSoFar                  Number of team drivers who have run a stint,
     *  DCLapStatus                     Status of driver change lap requirements,
     *  DisplayUnits                    Default units for the user interface 0 = english 1 = metric,
     *  EnterExitReset                  Indicate action the reset key will take 0 enter 1 exit 2 reset,
     *  LapBestNLapLap                  Player last lap in best N average lap time (Time Trial Only),
     *  LapBestNLapTime                 Player best N average lap time, s (Time Trial Only)
     *  LapLasNLapSeq                   Player num consecutive clean laps completed for N average (Time Trial Only),
     *  LapLastNLapTime                 Player last N average lap time, s (Time Trial Only)
     *  PlayerCarClassPosition          Players class position in race,
     *  PlayerCarPosition               Players position in race,         
     * @return true if the data is from data recorded after this build
     */
    public boolean build_June_9_2015() {
         Calendar cal = Calendar.getInstance(); cal.set(2015, 5, 9, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }
    
    /**
     * - Add support for fast repair to telemetry pit script commands. 
     * - Log pending pit service request to telemetry as "PitSvXXX". 
     *  PitSvFlags is a bitfield defines in irsdk_defines.h as irsdk_PitSvFlags and covers all the check boxes. 
     *  "PitSvFlags", irsdk_bitField, "Bitfield of pit service checkboxes", "irsdk_PitSvFlags"
     *  "PitSvLFP",   irsdk_float,    "Pit service left front tire pressure", "kPa"
     *  "PitSvRFP",   irsdk_float,    "Pit service right front tire pressure", "kPa"
     *  "PitSvLRP",   irsdk_float,    "Pit service left rear tire pressure", "kPa"
     *  "PitSvRRP",   irsdk_float,    "Pit service right rear tire pressure", "kPa"
     *  "PitSvFuel",  irsdk_float,    "Pit service fuel add amount", "kg" or "l" (See header)
     *  
     *  enum irsdk_PitSvFlags
     *  {
     *      irsdk_LFTireChange      = 0x0001,
     *      irsdk_RFTireChange      = 0x0002,
     *      irsdk_LRTireChange      = 0x0004,
     *      irsdk_RRTireChange      = 0x0008,
     *  
     *      irsdk_FuelFill          = 0x0010,
     *      irsdk_WindshieldTearoff = 0x0020,
     *      irsdk_FastRepair        = 0x0040
     *  };
     *
     * - Log live weather to telemetry.
     *   static irsdkVar irsdkWeatherType("WeatherType", NULL, irsdk_int, 1, "Weather type (0=constant, 1=dynamic)", "", IRSDK_LOG_ALL);
     *   static irsdkVar irsdkSkies("Skies", NULL, irsdk_int, 1, "Skies (0=clear/1=p cloudy/2=m cloudy/3=overcast)", "", IRSDK_LOG_ALL);
     *   static irsdkVar irsdkAirDensity("AirDensity", NULL, irsdk_float, 1, "Density of air at start/finish line", "kg/m^3", IRSDK_LOG_ALL);
     *   static irsdkVar irsdkAirPressure("AirPressure", NULL, irsdk_float, 1, "Pressure of air at start/finish line", "Hg", IRSDK_LOG_ALL);
     *   static irsdkVar irsdkWindVel("WindVel", NULL, irsdk_float, 1, "Wind velocity at start/finish line", "m/s", IRSDK_LOG_ALL);
     *   static irsdkVar irsdkWindDir("WindDir", NULL, irsdk_float, 1, "Wind direction at start/finish line", "rad", IRSDK_LOG_ALL);
     *   static irsdkVar irsdkRelativeHumidity("RelativeHumidity", NULL, irsdk_float, 1, "Relative Humidity", "%", IRSDK_LOG_ALL);
     *   static irsdkVar irsdkFogLevel("FogLevel", NULL, irsdk_float, 1, "Fog level", "%", IRSDK_LOG_ALL);
     * 
     * - Log the driver's fuel tank size to session string as "DriverCarFuelMaxLtr". Log the fuel restriction percent (if any) to session string as "DriverCarMaxFuelPct". Multiplying both together will give you the true fuel limit for this session. 
     * - Renamed "CarClassMaxFuel" to "CarClassMaxFuelPct" to better reflect the fact that it is a percent of the tank that you are allowed to use. 
     * - Dump information about the current setup to the session string 
     *   DriverInfo: 
     *   DriverSetupName: path 
     *   DriverSetupIsModified: bool 
     *   DriverSetupLoadTypeName: [invalid|user|iracing|baseline|current|default|fixed|shared] 
     *   DriverSetupPassedTech: bool 
     * - Fix a bug where not all cars had a properly set carClassColor in a mixed class race. 
     *
     * @return true if the data is from data recorded after this build
     */
    public boolean build_september_8_2015() {
        Calendar cal = Calendar.getInstance(); cal.set(2015, 8, 8, 0, 0, 0);
        long sessionDate = getHeader().getSubHeader().getSessionStartDate();
        return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }
    

    /**
     * - New telemetry values have been created, including:
     *  - - dpFWingAngle - Pitstop front wing adjustment
     *  - - dpRWingAngle - Pitstop rear wing adjustment
     *  - - dpFUFangleIndex - Pitstop front upper flap adjustment
     *  - - dpRrPerchOffsetm - Pitstop right rear spring offset adjustment
     *  - - dpLrWedgeAdj - Pitstop lr spring offset adjustment
     *  - - dpRrWedgeAdj - Pitstop rr spring offset adjustment
     *  - - YawNorth - Yaw orientation relative to north, can be directly compared against wind direction
     *  - - TrackTempCrew - Average of spot temperature of track measured by crew around track
     *  
     *  - Fixed bug that caused CarIdxF2Time to return an invalid float when a car was not in the world.
     *  
     *  - Discovered an issue with SessionLapsRemain and added a new SessionLapsRemainEx parameter that more accurately reflects the laps remaining in the current session. The old parameter has been left in place, just in case.
     *  
     *  - PitSvFuel units have been changed from kilograms to liters to better match the rest of the fuel telemetry values.
     *  
     *  - Changed lfTireColdPressPa, rfTireColdPressPa, lrTireColdPressPa, rrTireColdPressPa to log the pending pitstop tire pressure and not the last recorded pressure so you can monitor changes to the black box. This amounts to the same thing if the user is not adjusting their black box.
     *  
     *  - New SessionString parameters have been created, including:
     *  - - DriverInfo:PaceCarIdx: So you can more easily identify the pace car
     *  - - DriverInfo:Drivers[]:CarIsPaceCar: and DriverInfo:Drivers[]:CarIsAI: So you can more easily sort out who is a competitor
     *  - - WeekendInfo:TrackNorthOffset: So you can match up your car orientation and wind direction
     *  - - WeekendInfo:TrackCleanup: Indicating if the track is cleaned between sessions
     *  - - WeekendInfo:TrackDynamicTrack: Indicating if the track uses the dynamic surface model
     *  - - SessionString SessionInfo:Sessions[]:SessionTrackRubberState: Indicating how much rubber is on the track at the start of the session 
     * @return true if the data is from data recorded after this build
     */
    public boolean build_december_7_2015() {
         Calendar cal = Calendar.getInstance(); cal.set(2015, 11, 7, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }
    
    /**
     *   dcMGUKDeployAdapt : In car MGU-K adaptive deployment mode adjustment
     *   dcMGUKDeployFixed : In car MGU-K fixed deployment mode adjustment
     *   dcMGUKRegenGain : In car MUG-K re-gen gain adjustment
     *   EnergyBatteryToMGU-KLap : Electrical energy from battery to MGU-K per lap
     *   EnergyBudgetBattToMGU-KLap : Budgeted electrical energy from battery to MGU-K per lap
     *   EnergyERSBattery : Engine ERS battery charge
     *   PowerMGU-H : Engine MGU-H mechanical power
     *   PowerMGU-K : Engine MGU-K mechanical power
     *   TorqueMGU-K : Engine MGU-K mechanical torque
     * @return true if the data is from data recorded after this build
     */
    public boolean build_december_12_2015() {
         Calendar cal = Calendar.getInstance(); cal.set(2015, 11, 12, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }
    
    /**
     *  McLaren MP4-30
     *  
     *  - DRS Status can now be seen in telemetry. The variable has four possible values:
     *  - - 0 = Inactive
     *  - - 1 = Available in Next Zone
     *  - - 2 = Available and in a DRS Zone
     *  - - 3 = Active          \
     * @return true if the data is from data recorded after this build
     */
    public boolean build_december_21_2015() {
         Calendar cal = Calendar.getInstance(); cal.set(2015, 11, 21, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }
    
    /**
     * - A new telemetry item "LapCompleted" has been added which contains the number of laps completed by your car (Similar to "Lap" which contains your car's currently started lap). At a circuit event, "CarIdxLapCompleted" will always be 1 less than "CarIdxLap", since the finish line both finishes one lap and starts the next. But at the Nürburgring Tourist config, where the start and finish are separate, completing a lap does not start the next one.
     *  
     *  - A new telemetry item "CarIdxLapCompleted" has been added which is an array containing the number of laps completed for each CarIdx (Similar to "CarIdxLap" which contains the currently started lap).
     *  
     *  - Telemetry items "LapDistPct" and "CarIdxLapDistPct" have been updated to export "0.0" to "1.0" correctly for the Nürburgring Tourist config, with "1.0" being at the finish line. The number will continue to increase above "1.0" in the non-timed area, and wrap back to "0.0" at the start line.
     *  
     *  - The state of "TrackCleanup" has been inverted. Now a value of "0" indicates that the track is not being cleaned up, and a value of "1" indicates the track is being cleaned up.
     *  
     *  - Renamed several telemetry parameters to remove the unacceptable characters, "-" and, " ", as follows:
     *  - - PowerMGU-K = PowerMGU_K
     *  - - TorqueMGU-K = TorqueMGU_K
     *  - - PowerMGU-H = PowerMGU_H
     *  - - EnergyBatteryToMGU-KLap = EnergyBatteryToMGU_KLap
     *  - - EnergyBudgetBattToMGU-KLap = EnergyBudgetBattToMGU_KLap
     *  - - DRS Status = DRS_Status
     *  
     * @return true if the data is from data recorded after this build
     */
    public boolean build_january_6_2016() {
         Calendar cal = Calendar.getInstance(); cal.set(2016, 0, 6, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }

    /**
     * app.ini[Pit Service]autoResetFastRepair
     *    
     * @return true if the data is from data recorded after this build
     */
    public boolean build_march_30_2016() {
         Calendar cal = Calendar.getInstance(); cal.set(2016, 2, 30, 0, 0, 0);
         long sessionDate = getHeader().getSubHeader().getSessionStartDate();
         return sessionDate >= (cal.getTimeInMillis() / 1000L);
    }
    
    /**
     *  - Three new telemetry variables related to incident count have been added. If the event is a team race, the incident count is broken down in different ways; but if not, then all three variables will equal the same value. The variables are as follows: 
     *  - - "PlayerCarTeamIncidentCount" = Incident count this session for your whole team. 
     *  - - "PlayerCarMyIncidentCount" = Incident count this session just for you. 
     *  - - "PlayerCarDriverIncidentCount" = Incident count this session for the current team driver. 
     *  
     *  - A new session string entry, "WeekendInfo:WeekendOptions:IncidentLimit:," has been added that indicates the maximum incidents you can receive before being ejected from the session. 
     *  
     *  - A new telemetry variable, "PlayerTrackSurface," has been added that indicates the track surface under the player vehicle. This is available both live and on disk. 
     *  
     *  - A new telemetry variable, "PlayerCarIdx," has been added that allows developers to access the current player's car index without loading up the session string. This is available both live and on disk. 
     *  
     *  - Fixed a bug where setup information in a telemetry session string could become stale. 
     * @return true if the data is from data recorded after this build
     */
    public boolean build_june_6_2016() {
        Calendar cal = Calendar.getInstance(); cal.set(2016, 5, 6, 0, 0, 0);
        long sessionDate = getHeader().getSubHeader().getSessionStartDate();
        return sessionDate >= (cal.getTimeInMillis() / 1000L);
   }
    
    //These tags are placed in the recorded file to mark each object
    protected final static String HEADER_TAG        = "[HH]";
    protected final static String SUBHEADER_TAG     = "[SH]";
    protected final static String SESSIONINFO_TAG   = "[SI]";
    protected final static String VARHEADERS_TAG    = "[VH]";
    protected final static String VARS_TAG          = "[VB]";
    protected final static String COMMAND_TAG       = "[CM]";
    protected final static String OPTIONS_TAG       = "[OP]";
    protected final static String EOF_TAG           = "[EF]";    //may never get written if finalize doesn't get called.

    private String m_record = null;
    private String m_play = null;
    private FileOutputStream m_fos;
    private BufferedOutputStream m_bos;
    private DataOutputStream m_dos = null;

    byte m_int_buffer[] = new byte[4];
    ByteBuffer m_int_bytebuffer = (ByteBuffer.wrap(m_int_buffer)).order(ByteOrder.LITTLE_ENDIAN);

    private String m_cmdstring = "";
    protected Map<String,String> m_options = new HashMap<String,String>();
    private Header m_header = null;
    private SessionInfo m_sessioninfo = null;
    private VarHeaders m_varheaders = null;
    private Vars m_vars = null;

    protected IODriver() {}

    public String Record() { return m_record; }
    public void Record (String record) {
        if (record != null && !record.isEmpty()) {
            java.io.File file = new java.io.File(record);
            try {
                m_fos = new FileOutputStream(file);
                m_bos = new BufferedOutputStream(m_fos);
                m_dos = new DataOutputStream(m_bos);
                Server.logger().info(String.format("Recording to (%s)",record));

                //Put all options here so they will be written to the recorded file first
                Server.logger().info(String.format("IODriver().Record() Options: %s=%s", "autoResetPitBox", readOption("autoResetPitBox")));
                Server.logger().info(String.format("IODriver().Record() Options: %s=%s", "autoResetPitBox", readOption("autoResetFastRepair")));
            }
            catch (FileNotFoundException e){
                Server.logStackTrace(Level.SEVERE,String.format("Cannot Create Record file, [%s]. %s. Exiting...",record,e.getMessage()),e);
                System.exit(1);
            }
            catch (SecurityException e) {
                Server.logStackTrace(Level.SEVERE,String.format("Cannot Create Record file, [%s]. %s. Exiting...",record,e.getMessage()),e);
                System.exit(1);
            }
        }
        else
        if (m_dos != null)
            close();
        m_record = record;
    }

    public String getPlay() { return m_play; }

    private Header m_header_empty = null;
    public Header getHeader() {
        if (m_header == null) { //if we don't have one yet allocate an empty one
            if (m_header_empty == null) {
                byte buffer[] = new byte[Header.SIZEOF_HEADER];
                m_header_empty = new Header(ByteBuffer.wrap(buffer));
            }
            return m_header_empty;
        }
        return m_header;
    }

    private SessionInfo m_sessioninfo_empty = null; 
    public SessionInfo getSessionInfo () {
        if (m_sessioninfo == null) { //if we don't have one yet allocate an empty one
            if (m_sessioninfo_empty == null) {
                byte buffer[] = {'-','-','-','\n','.','.','.','\n'};
                m_sessioninfo_empty = new SessionInfo(getHeader(),ByteBuffer.wrap(buffer));
            }
            return m_sessioninfo_empty;
        }
        return m_sessioninfo;
    }

    private VarHeaders m_varheaders_empty = null;
    public VarHeaders  getVarHeaders () {
        if (m_varheaders == null) { //if we don't have one yet allocate an empty one
            if (m_varheaders_empty == null) {
                byte buffer[] = new byte[0];
                m_varheaders_empty = new VarHeaders(getHeader(),ByteBuffer.wrap(buffer));
            }
            return m_varheaders_empty;
        }
        return m_varheaders;
    }
    
    private Vars m_vars_empty = null;
    public Vars getVars () {
        if (m_vars == null) { //if we don't have one yet allocate an empty one
            if (m_vars_empty == null) {
                byte buffer[] = new byte[0];
                m_vars_empty = new Vars(getVarHeaders(),ByteBuffer.wrap(buffer));
            }
            return m_vars_empty;
        }
        return m_vars;
    }

    //this method must be overridden to actually perform the read, abstract
    public boolean Read(long startingtick,long endingtick) {
        return false;
    }

    public String getCommand() {
        String s = m_cmdstring;
        m_cmdstring = "";
        return s;
    }

    public void setCommand(String cmd) {
        String xcmd = cmd;
        //translate the old commands to the new ones for older recorded files with commands in them.
        if (cmd.toUpperCase().startsWith("SETCARTIREPRESSUREPIT/")) {
            xcmd = "Car/REFERENCE/Gauge/TirePressure" + cmd.substring(22,24) + "/setValueNext/" + cmd.substring(25);
        }
        else
        if (cmd.toUpperCase().startsWith("SETCARFUELLEVELPIT/")) {
            xcmd = "Car/REFERENCE/Gauge/FuelLevel/setValueNext/" + cmd.substring(19);
        }
        else
        if (cmd.toUpperCase().startsWith("SETCARWINDSHIELDTEAROFFPIT/")) {
            xcmd = "Car/REFERENCE/Gauge/WindshieldTearoff/setChangeFlag/" + cmd.substring(27);
        }
        m_cmdstring = xcmd;
    }

    public void recordCommand(String cmdstring) {
        Server.logger().info(String.format("%s.recordCommand(%s)\n", this.getClass().getName(),cmdstring));
        if (m_dos != null) {
            try {
                m_dos.write(COMMAND_TAG.getBytes());
                m_int_bytebuffer.putInt(0, cmdstring.length());
                m_dos.write(m_int_buffer);
                m_dos.write(cmdstring.getBytes());
            }
            catch (IOException e) {
                Server.logStackTrace(Level.SEVERE,String.format("Caught Exception(%s) writing to Command file %s\n",e.getMessage(),Record()),e);
                System.exit(1);
            }
        }
    }

    public void recordOption(String option,String value) {
        Server.logger().info(String.format("recordOption(%s=%s)",option,value));
        if (m_dos != null) {
            try {
                m_dos.write(OPTIONS_TAG.getBytes());
                m_int_bytebuffer.putInt(0, option.length() + value.length() + 1);
                m_dos.write(m_int_buffer);
                m_dos.write(option.getBytes());
                m_dos.write("=".getBytes());
                m_dos.write(value.getBytes());
            }
            catch (IOException e) {
                Server.logStackTrace(Level.SEVERE,String.format("Caught Exception(%s) writing Option(%s=%s) to file %s",e.getMessage(),option,value,Record()),e);
                System.exit(1);
            }
        }
    }

    protected boolean ProcessBuffers (
      ByteBuffer header_buffer
    , ByteBuffer sessioninfo_buffer
    , ByteBuffer varheaders_buffer
    , ByteBuffer var_buffer
    , ByteBuffer subheader_buffer
    ) {
        try {
            Header header = m_header;    //start with the last header, if we are not passed one.

            if (header_buffer != null) {
                header = new Header(header_buffer);
                if (!header.isValid())
                    return false;

                if (m_dos != null){
                    m_dos.write(HEADER_TAG.getBytes());
                    m_int_bytebuffer.putInt(0, header_buffer.capacity());
                    m_dos.write(m_int_buffer);
                    m_dos.write(header_buffer.array());

                    if (subheader_buffer != null) {
                        header.getSubHeader(new SubHeader(subheader_buffer));
                        m_dos.write(SUBHEADER_TAG.getBytes());
                        m_int_bytebuffer.putInt(0, subheader_buffer.capacity());
                        m_dos.write(m_int_buffer);
                        m_dos.write(subheader_buffer.array());
                    }
                }
            }

            //if the session info data has changed, reload it
            if (sessioninfo_buffer != null) {
                m_sessioninfo = new SessionInfo(header,sessioninfo_buffer);
                if (m_dos != null){
                    m_dos.write(SESSIONINFO_TAG.getBytes());
                    m_int_bytebuffer.putInt(0, sessioninfo_buffer.capacity());
                    m_dos.write(m_int_buffer);
                    m_dos.write(sessioninfo_buffer.array());
                }
            }

            if (varheaders_buffer != null) {
                m_varheaders = new VarHeaders(header,varheaders_buffer);
                if (m_dos != null){
                    m_dos.write(VARHEADERS_TAG.getBytes());
                    m_int_bytebuffer.putInt(0, varheaders_buffer.capacity());
                    m_dos.write(m_int_buffer);
                    m_dos.write(varheaders_buffer.array());
                }
            }

            if (var_buffer != null) {
                //if we don't have the headers yet, reject it
                if (m_varheaders == null)
                    return false;
                
                m_vars = new Vars(m_varheaders,var_buffer);

                //check for bad data and reject it.
                if (m_vars.getInteger("SessionUniqueID") == 0
                &&  m_vars.getInteger("SessionState") == 0
                ) return false;

                if (m_dos != null){
                    m_dos.write(VARS_TAG.getBytes());
                    m_int_bytebuffer.putInt(0, var_buffer.capacity());
                    m_dos.write(m_int_buffer);
                    m_dos.write(var_buffer.array());
                    m_dos.flush();
                }
            }

            if (header_buffer != null) {
                m_header = header;
            }

            return true;
        }
        catch (IOException e) {
            Server.logStackTrace(Level.SEVERE,String.format("Caught Exception(%s) writing to Record file %s",e.getMessage(),Record()),e);
            System.exit(1);
        }
        return false;
    }

    boolean m_eof = false;

    public boolean isEOF() { return m_eof; }
    public boolean isEOF(boolean flag) { return m_eof = flag; }

    public void WaitForData() {}

    public boolean isConnected() {
        return (getHeader().getStatus() & StatusField.stConnected) != 0;
    }

    public void broadcastMsg(short msg, short var1, short var2, short var3) {}
    public void broadcastMsg(short msg, short var1, int var2) {}

    public void close() {
        try {
            if (m_dos != null) {
                m_dos.write(EOF_TAG.getBytes());
                m_dos.flush();
                m_dos.close();
                Server.logger().info(String.format("Recording stopped closing (%s)\n",m_record));
            }
        }
        catch (IOException e) {
            m_dos = null;
            Server.logStackTrace(Level.SEVERE,String.format("Caught Exception(%s) writing EOF to Record file %s\n",e.getMessage(),Record()),e);
            System.exit(1);
        }
        m_dos = null;
    }

    public String dataDir() {
        //first get the My Documents location from the registry
        //At the time of this writing 2015, you can only change the folder found in the documents folder
        //with the datadir.txt file. iRacing does not support it being in another location.
        //if you do change the location, or your an iRacing tester, then you need to pass in the
        //iracing-datadir option to tell me where the folder is you are testing.
        
        String s = FindFile.getUserDocumentsPath();
        if (s != null && !s.isEmpty()) {
            //TODO: Can I automate this by looking for the iRacingService.exe process path and open the datadir.txt file?
            String datadir = Server.getArg("iracing-datadir","iRacing");
            return s + "\\" + datadir;
        }
        return null;
    }
    
    Properties m_app = null;
    Properties m_renderer = null;

    public String readOption(String key) {

        //if we don't have the value yet
        if (!m_options.containsKey(key)) {

            //if the app.ini file has not been loaded
            if (m_app == null) {
                m_app = new Properties();

                String s = dataDir();
                if (s != null && !s.isEmpty()) {
                    
                    String path = s + "\\" + Server.getArg("iracing-app-file","app.ini");
                    try {
                        FileInputStream in = new FileInputStream(path);
                        Server.logger().info("iRacing.IODriver.Loading: "+path);
                        m_app.load(in);
                        in.close();
                    } catch (FileNotFoundException e) {
                        Server.logStackTrace(Level.WARNING,"FileNotFoundException",e);
                    } catch (IOException e) {
                        Server.logStackTrace(Level.SEVERE,"IOException",e);
                    }
                }
            }

            //if the rendererDX11.ini file has not been loaded
            if (m_renderer == null) {
                m_renderer = new Properties();

                String s = dataDir();
                if (s != null && !s.isEmpty()) {
                    
                    String path = s + "\\" + Server.getArg("iracing-renderer-file","rendererDX11Monitor.ini");
                    try {
                        FileInputStream in = new FileInputStream(path);
                        Server.logger().info("iRacing.IODriver.Loading: "+path);
                        m_renderer.load(in);
                        in.close();
                    } catch (FileNotFoundException e) {
                        Server.logStackTrace(Level.WARNING,"FileNotFoundException",e);
                    } catch (IOException e) {
                        Server.logStackTrace(Level.SEVERE,"IOException",e);
                    }
                }
            }

            //see if this option is in the app.ini file
            String s = m_app.getProperty(key);
            if (s != null && !s.isEmpty()) {
                //remove the comments
                String a[] = s.split(";");
                if (a.length > 0) {
                    m_options.put(key, a[0].trim());
                    recordOption(key,m_options.get(key));
                }
            }
            else {
                s = m_renderer.getProperty(key);
                if (s != null && !s.isEmpty()) {
                    //remove the comments
                    String a[] = s.split(";");
                    if (a.length > 0) {
                        m_options.put(key, a[0].trim());
                        recordOption(key,m_options.get(key));
                    }
                }
                else {
                    m_options.put(key, "");
                }
            }
        }

        return m_options.get(key);
    }

    public int getAutoResetPitBox() {
        try {
            String s = readOption("autoResetPitBox");
            if (!s.isEmpty()) {
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
        }

        return 1; //iRacingDefault if you can't read the file
    }

    public int getAutoResetFastRepair() {
        try {
            String s = readOption("autoResetFastRepair");
            if (!s.isEmpty()) {
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
        }

        return 1; //default if you can't read the file
    }

    public int getHideCarNum() {
        try {
            String s = readOption("hideCarNum");
            if (!s.isEmpty()) {
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
        }

        return 0; //iRacingDefault if you can't read the file
    }

    public int getReportPitBoxCount() {
        try {
            String s = readOption("reportPitboxCount");
            if (!s.isEmpty()) {
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
        }

        return 1; //iRacingDefault if you can't read the file
    }

    public void finalize() throws Throwable {
        close();
        super.finalize();
    }
}


