package com.SIMRacingApps.SIMPlugins.iRacing;

import com.SIMRacingApps.SIMPlugin.SIMPluginException;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.SIMPlugins.iRacing.VarHeaders.VarHeader;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2020 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class iRacingTrack extends com.SIMRacingApps.Track {

    iRacingSIMPlugin m_SIMPlugin;
    
    public iRacingTrack(iRacingSIMPlugin SIMPlugin) {
        super(SIMPlugin);
        m_SIMPlugin = SIMPlugin;
    }

    @Override
    protected boolean _loadTrack() {
        boolean b = super._loadTrack();
        if (b) {
            try {
                Server.logger().info(String.format("Track %s, Lat = %s, Lon = %s",
                        m_SIMPlugin.getData("/iRacing/WeekendInfo/TrackName").getString(),
                        m_SIMPlugin.getData("/iRacing/WeekendInfo/TrackLatitude").getString(),
                        m_SIMPlugin.getData("/iRacing/WeekendInfo/TrackLongitude").getString()
                ));
            } catch (SIMPluginException e) {
            }
        }
        return b;
    }
    
    @Override
    public Data getCategory() {
        Data d = super.getCategory();
        d.setState(Data.State.OFF);
        
        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","Category");
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getCity() {
        Data d = super.getCity();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackCity");
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getConfiguration() {
        Data d = super.getConfiguration();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackConfigName");
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }
    
    @Override
    public Data getCountry() {
        Data d = super.getCountry();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackCountry");
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getDescription() {
        Data d = super.getDescription();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackDisplayName");
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getLength(String UOM) {
        Data d = super.getLength(UOM);
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackLength").split(" ");
            if (s.length == 2) {
                d.setValue(Double.parseDouble(s[0]));
                d.setUOM(s[1]);
                d.setState(Data.State.NORMAL);
                d = d.convertUOM(UOM);
            }
            int displayUnits = m_SIMPlugin.getIODriver().getVars().getInteger("DisplayUnits");
            if (displayUnits == 0)
                d = d.convertUOM("IMPERIAL");
            else
            if (displayUnits == 1)
                d = d.convertUOM("METRIC");
        }
        return d;
    }

    @Override
    public Data getName() {
        Data d = super.getName();
        String s = Server.getArg("iracing-track");
        d.setState(Data.State.OFF);

        if (!s.isEmpty()) {
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        else
        if (m_SIMPlugin.isConnected()) {
            s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackName");
            d.setValue(s);
            d.setState(Data.State.NORMAL);
        }
        return d;
    }

    @Override
    public Data getPitSpeedLimit(String UOM) {
        Data d = super.getPitSpeedLimit("");
        d.setState(Data.State.OFF);

        String trackUOM = d.getUOM();
        if (m_SIMPlugin.isConnected()) {
            String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackPitSpeedLimit").split(" ");
            if (s.length == 2) {
                d.setValue(Double.parseDouble(s[0]),s[1],Data.State.NORMAL);
            }
        }

        d = d.convertUOM(trackUOM);
        int displayUnits = m_SIMPlugin.getIODriver().getVars().getInteger("DisplayUnits");
        if (displayUnits == 0)
            d = d.convertUOM("IMPERIAL");
        else
        if (displayUnits == 1)
            d = d.convertUOM("METRIC");
//This creates a circular reference when the speed limit is requested for the car.
//If the car's UOM is different from the tracks, the car will need to convert it.        
//        d = d.convertUOM(m_SIMPlugin.getSession().getCar("REFERENCE").getGauge(Gauge.Type.SPEEDOMETER).getUOM().getString()).convertUOM(UOM);
        return d.convertUOM(UOM);
    }

    @Override
    public Data getTemp(String UOM) {
        Data d = super.getTemp(UOM);
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String sUOM = d.getUOM();
            
            //David is going to put TrackTempCrew in the next build, probably Dec 2015.
            //http://members.iracing.com/jforum/posts/list/1850/1470675.page#9254368
            //TrackTemp was designed to change with the position of the sun and the air temp, 
            //neither of which change during a race as of Sept 2015.
            //TrackTempCrew is going to be real-time readings of various points on the track averaged.
            //Once he does, this code should kick in and use it.
            VarHeader trackTemp = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("TrackTempCrew",m_SIMPlugin.getIODriver().getVars());
            //If not there fall back to the static one.
            if (trackTemp == null)
                trackTemp = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("TrackTemp",m_SIMPlugin.getIODriver().getVars());
            
            if (trackTemp != null) {
                d.setValue((double)trackTemp.Value,trackTemp.Unit,Data.State.NORMAL);
            }
            else {
                String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","WeatherTemp").split(" ");
                //String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackAirTemp").split(" ");
                //String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackSurfaceTemp").split(" ");
                if (s.length == 2) {
                    d.setValue(Double.parseDouble(s[0]));
                    d.setUOM(s[1]);
                    d.setState(Data.State.NORMAL);
                }
                
                //haven't actually seen this populated, but can't hurt to try and read it.
                s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackSurfaceTemp").split(" ");
                if (s.length == 2 && Double.parseDouble(s[0]) > 0.0) {
                    d.setValue(Double.parseDouble(s[0]));
                    d.setUOM(s[1]);
                    d.setState(Data.State.NORMAL);
                }
            }
            d = d.convertUOM(sUOM);
            int displayUnits = m_SIMPlugin.getIODriver().getVars().getInteger("DisplayUnits");
            if (displayUnits == 0)
                d = d.convertUOM("IMPERIAL");
            else
            if (displayUnits == 1)
                d = d.convertUOM("METRIC");
        }
        return d.convertUOM(UOM);
    }
    
    @Override
    public Data getType() {
        Data d = super.getType();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackType");
            d.setState(Data.State.NORMAL);

            if (s.equals("long oval"))
                d.setValue(Type.LONG_OVAL);
            else
            if (s.equals("medium oval")
            || s.equals("mile oval"))    //dover
                d.setValue(Type.MEDIUM_OVAL);
            else
            if (s.equals("short oval"))
                d.setValue(Type.SHORT_OVAL);
            else
            if (s.equals("super speedway"))
                d.setValue(Type.SUPER_SPEEDWAY);
            else
            if (s.equals("road course"))
                d.setValue(Type.ROAD_COURSE);
            else {
                d.setValue("ERROR: TrackType: "+Type.UNKNOWN);
                d.setState(Data.State.ERROR);
            }
        }
        return d;
    }

    @Override
    public Data getWeatherFogLevel() {
        Data d = super.getWeatherFogLevel();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String sUOM = d.getUOM();
            //This was added in Sept 2015
            VarHeader fog = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("FogLevel",m_SIMPlugin.getIODriver().getVars());
            if (fog != null) {
                d.setValue((double)fog.Value * 100.0,fog.Unit,Data.State.NORMAL);
                d = d.convertUOM(sUOM);
            }
            else {
                String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","FogLevel").split(" ");
                if (s.length == 2) {
                    d.setValue(Double.parseDouble(s[0]));
                    d.setUOM(s[1]);
                    d.setState(Data.State.NORMAL);
                }
            }
        }
        return d;
    }

    @Override
    public Data getWeatherRelativeHumidity() {
        Data d = super.getWeatherRelativeHumidity();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String sUOM = d.getUOM();
            //This was added in Sept 2015
            VarHeader rh = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("RelativeHumidity",m_SIMPlugin.getIODriver().getVars());
            if (rh != null) {
                d.setValue((double)rh.Value * 100.0,rh.Unit,Data.State.NORMAL);
            }
            else {
                //String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","RelativeHumidity").split(" ");
                String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackRelativeHumidity").split(" ");
                if (s.length == 2) {
                    d.setValue(Double.parseDouble(s[0]));
                    d.setUOM(s[1]);
                    d.setState(Data.State.NORMAL);
                }
            }
            d = d.convertUOM(sUOM);
        }
        return d;
    }

    @Override
    public Data getWeatherSkies() {
        Data d = super.getWeatherSkies();
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String sUOM = d.getUOM();
            //This was added in Sept 2015
            VarHeader skies = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("Skies",m_SIMPlugin.getIODriver().getVars());
            if (skies != null) {
                String s = String.format("skies=%d",(int)skies.Value);
                switch ((int)skies.Value) {
                    case 0: s = "Clear"; break;
                    case 1: s = "Partly Cloudy"; break;
                    case 2: s = "Mostly Cloudy"; break;
                    case 3: s = "Overcast"; break;
                }
                d.setValue(s,"",Data.State.NORMAL);
                d = d.convertUOM(sUOM);
            }
            else {
                String s  = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackSkies");
                String s2 = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","Skies");
                if (s.isEmpty())
                    s = s2;
                if (s.isEmpty())
                    s = "Partly Cloudy";
                d.setValue(s);
                d.setState(Data.State.NORMAL);
            }
        }
        return d;
    }


    @Override
    public Data getWeatherTemp(String UOM) {
        Data d = super.getWeatherTemp(UOM);
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            String sUOM = d.getUOM();
            //This was added in Sept 2015
            VarHeader airTemp = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("AirTemp",m_SIMPlugin.getIODriver().getVars());
            if (airTemp != null && (double)airTemp.Value > 0.0) {
                d.setValue((double)airTemp.Value,airTemp.Unit,Data.State.NORMAL);
                d = d.convertUOM(sUOM);
            }
            else {
                String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","WeatherTemp").split(" ");
                //String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackAirTemp").split(" ");
                //String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackSurfaceTemp").split(" ");
                if (s.length == 2) {
                    d.setValue(Double.parseDouble(s[0]));
                    d.setUOM(s[1]);
                    d.setState(Data.State.NORMAL);
                    d = d.convertUOM(sUOM);
                }
            }
            int displayUnits = m_SIMPlugin.getIODriver().getVars().getInteger("DisplayUnits");
            if (displayUnits == 0)
                d = d.convertUOM("IMPERIAL");
            else
            if (displayUnits == 1)
                d = d.convertUOM("METRIC");
        }
        return d.convertUOM(UOM);
    }

    @SuppressWarnings("unused")
    @Override
    public Data getWeatherWindDirection(String UOM) {
        Data d = super.getWeatherWindDirection(UOM);
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
if (false) {
            String s = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","WindDirection");
            if (s.equals("N"))
                d.setValue(270.0,"deg");
            else
            if (s.equals("NE"))
                d.setValue(315.0,"deg");
            else
            if (s.equals("E"))
                d.setValue(0.0,"deg");
            else
            if (s.equals("SE"))
                d.setValue(45.0,"deg");
            else
            if (s.equals("S"))
                d.setValue(90.0,"deg");
            else
            if (s.equals("SW"))
                d.setValue(135.0,"deg");
            else
            if (s.equals("W"))
                d.setValue(180.0,"deg");
            else
            if (s.equals("NW"))
                d.setValue(225.0,"deg");
            
            d.setState(Data.State.NORMAL);
}
else {
            String sUOM = d.getUOM();
            
            //this was added in Sept 2015
            VarHeader windDir = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("WindDir",m_SIMPlugin.getIODriver().getVars());
            if (windDir != null && (double)windDir.Value >= 0.0) {
                d.setValue((double)windDir.Value,windDir.Unit,Data.State.NORMAL);
                d = d.convertUOM("deg");
                //N = 0 deg = 270 or -90
                //E = 90 deg = 0
                //W = 270 deg = 180
                //S = 180 deg = 90
                d.setValue(d.getDouble() - 90.0);  //degrees go clockwise from 3 o'clock, rad goes clockwise from 12 o'clock
                d.setValue(d.getDouble() % 360.0);
                if (d.getDouble() < 0.0)
                    d.setValue(d.getDouble() + 360.0);
            }
            else {
                String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackWindDir").split(" ");
                if (s.length == 2) {
                    d.setValue(Double.parseDouble(s[0]),s[1]);
                    d = d.convertUOM("deg");
                    //N = 0 deg = 270 or -90
                    //E = 90 deg = 0
                    //W = 270 deg = 180
                    //S = 180 deg = 90
                    d.setValue(d.getDouble() - 90.0);  //degrees go clockwise from 3 o'clock, rad goes clockwise from 12 o'clock

//for testing for future real-time wind direction changes, add in current time
//d.setValue(d.getDouble() + ((System.currentTimeMillis() / 100) % 360.0));

                    d.setValue(d.getDouble() % 360.0);
                    if (d.getDouble() < 0.0)
                        d.setValue(d.getDouble() + 360.0);
    
                    d.setState(Data.State.NORMAL);
                }
            }
            d = d.convertUOM(sUOM);
}
        }
        
        if (UOM.equalsIgnoreCase("TEXT")) {
            d = d.convertUOM("deg");
            
            //String s   = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","WindDirection");
            //Not going to use WindDirection because it was not changing in a realistic weather session.
            //instead, well map the degrees back to text
    
            if (d.getDouble() >= (0.0 - 22.5) && d.getDouble() <= (0.0 + 22.5))
                d.setValue("E");
            else
            if (d.getDouble() >= (45.0 - 22.5) && d.getDouble() <= (45.0 + 22.5))
                d.setValue("SE");
            else
            if (d.getDouble() >= (90.0 - 22.5) && d.getDouble() <= (90.0 + 22.5))
                d.setValue("S");
            else
            if (d.getDouble() >= (135.0 - 22.5) && d.getDouble() <= (135.0 + 22.5))
                d.setValue("SW");
            else
            if (d.getDouble() >= (180.0 - 22.5) && d.getDouble() <= (180.0 + 22.5))
                d.setValue("W");
            else
            if (d.getDouble() >= (225.0 - 22.5) && d.getDouble() <= (225.0 + 22.5))
                d.setValue("NW");
            else
            if (d.getDouble() >= (270.0 - 22.5) && d.getDouble() <= (270.0 + 22.5))
                d.setValue("N");
            else
            if (d.getDouble() >= (315.0 - 22.5) && d.getDouble() <= (315.0 + 22.5))
                d.setValue("NE");
            else
            if (d.getDouble() >= (360.0 - 22.5) && d.getDouble() <= (360.0 + 22.5))
                d.setValue("E");
    
            d.setState(Data.State.NORMAL);
            return d;
        }
    
        return d.convertUOM(UOM);
    }

    @Override
    public Data getWeatherWindSpeed(String UOM) {
        Data d = super.getWeatherWindSpeed(UOM);
        d.setState(Data.State.OFF);

        if (m_SIMPlugin.isConnected()) {
            //This was added in Sept 2015
            VarHeader windVel = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeaderValue("WindVel",m_SIMPlugin.getIODriver().getVars());
            if (windVel != null && (double)windVel.Value > 0.0) {
                String sUOM = d.getUOM();
                d.setValue((double)windVel.Value,windVel.Unit,Data.State.NORMAL);
                d = d.convertUOM(sUOM);
            }
            else {
                String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","WeekendOptions","WindSpeed").split(" ");
                //String s[] = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackWindVel").split(" ");
                if (s.length == 2) {
                    String sUOM = d.getUOM();
                    d.setValue(Double.parseDouble(s[0]));
                    d.setUOM(s[1]);
                    d.setState(Data.State.NORMAL);
                    d = d.convertUOM(sUOM);
//for testing for future real-time weather changes, add in current time
//d.setValue(d.getDouble() + ((System.currentTimeMillis() / 100) % 20.0));
                }
            }        
            int displayUnits = m_SIMPlugin.getIODriver().getVars().getInteger("DisplayUnits");
            if (displayUnits == 0)
                d = d.convertUOM("IMPERIAL");
            else
            if (displayUnits == 1)
                d = d.convertUOM("METRIC");
        }
        return d.convertUOM(UOM);
    }
    
}
/*
{
"WeekendInfo": {
"TrackName": "richmond",
"TrackID": 31,
"TrackLength": "1.16 km",
"TrackDisplayName": "Richmond International Raceway",
"TrackDisplayShortName": "Richmond",
"TrackConfigName": null,
"TrackCity": "Richmond",
"TrackCountry": "USA",
"TrackAltitude": "59.31 m",
"TrackLatitude": "37.591091 m",
"TrackLongitude": "-77.419665 m",
"TrackNumTurns": 4,
"TrackPitSpeedLimit": "63.97 kph",
"TrackType": "short oval",
"TrackWeatherType": "Realistic",
"TrackSkies": "Partly Cloudy",
"TrackSurfaceTemp": "0.00 C",
"TrackAirTemp": "0.00 C",
"TrackAirPressure": "29.71 Hg",
"TrackWindVel": "0.00 m/s",
"TrackWindDir": "4.42 rad",
"TrackRelativeHumidity": "48 %",
"TrackFogLevel": "0 %",
"SeriesID": 0,
"SeasonID": 0,
"SessionID": 58521678,
"SubSessionID": 14163409,
"LeagueID": 18,
"Official": 0,
"RaceWeek": 0,
"EventType": "Race",
"Category": "Oval",
"SimMode": "full",
"TeamRacing": 0,
"MinDrivers": 0,
"MaxDrivers": 1,
"DCRuleSet": "None",
"QualifierMustStartRace": 0,
"NumCarClasses": 1,
"NumCarTypes": 3,
"WeekendOptions": {
"NumStarters": 43,
"StartingGrid": "single file",
"QualifyScoring": "best lap",
"CourseCautions": "full",
"StandingStart": 0,
"Restarts": "double file lapped cars behind",
"WeatherType": "Realistic",
"Skies": "Partly Cloudy",
"WindDirection": "N",
"WindSpeed": "3.22 km/h",
"WeatherTemp": "25.56 C",
"RelativeHumidity": "55 %",
"FogLevel": "0 %",
"Unofficial": 1,
"CommercialMode": "consumer",
"NightMode": 1,
"IsFixedSetup": 1,
"StrictLapsChecking": "default",
"HasOpenRegistration": 1,
"HardcoreLevel": 0
},
"TelemetryOptions": {
"TelemetryDiskFile": ""
}
},  
*/