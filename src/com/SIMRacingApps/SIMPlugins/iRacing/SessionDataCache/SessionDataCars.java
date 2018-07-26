package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import com.SIMRacingApps.Session;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SessionDataCars extends SessionData {

    private int m_maxCars = 64;
    private Map<Integer,iRacingCar> m_cars = new HashMap<Integer,iRacingCar>();
    private int m_numberOfCars = 0;
    private int m_SOF = 0;
    private iRacingCar m_fastestCar;    //car who's last lap was the fastest
    private iRacingCar m_bestCar;       //car who's got the best lap overall
    
    public SessionDataCars(iRacingSIMPlugin SIMPlugin) {
        super(SIMPlugin, "Cars", 64, "integer",State.OFF);
        m_maxCars = 64;
        m_SIMPlugin = SIMPlugin;
    }

    public SessionDataCars(iRacingSIMPlugin SIMPlugin, String name) {
        super(SIMPlugin, name, 64, "integer",State.OFF);
        m_maxCars = 64;
        m_SIMPlugin = SIMPlugin;
    }

    public SessionDataCars(iRacingSIMPlugin SIMPlugin, String name, int value) {
        super(SIMPlugin, name, value, "integer",State.OFF);
        m_maxCars = value;
        m_SIMPlugin = SIMPlugin;
    }
    
    public SessionDataCars(iRacingSIMPlugin SIMPlugin, String name, int value, String UOM) {
        super(SIMPlugin, name,value,UOM,State.OFF);
        m_maxCars = value;
        m_SIMPlugin = SIMPlugin;
    }
    
    public SessionDataCars(iRacingSIMPlugin SIMPlugin, String name, int value, String UOM, String state) {
        super(SIMPlugin, name,value,UOM,state);
        m_maxCars = value;
        m_SIMPlugin = SIMPlugin;
    }

    public Object getValue(String name) {
        this._update();
        super.setState(State.NORMAL);
        return super.getValue(name);
    }
    
    public int getNumberOfCars() { 
        this._update();
        return m_numberOfCars; 
    }
    
    public int getSOF() { 
        this._update();
        return m_SOF; 
    }

    public iRacingCar getBest() {
        this._update();
        return m_bestCar;
    }
    
    public iRacingCar getCar(int caridx) {
        this._update();
//        if (!m_cars.containsKey(caridx))
//            Server.logger().severe(String.format("caridx(%d) not found", caridx));
        return m_cars.get(caridx);
    }

    public iRacingCar getFastest() {
        this._update();
        return m_fastestCar;
    }
    
    public iRacingCar getPaceCar() {
        this._update();
        for (Iterator<Entry<Integer, iRacingCar>> itr = m_cars.entrySet().iterator(); itr.hasNext();) {
            iRacingCar car = itr.next().getValue();
            if (car.isPaceCar())
                return car;
        }
        return null;
    }
    
    public iRacingCar getPitstall() {
        this._update();
        return m_cars.get(-2);
    }

    public iRacingCar getByName(String name) {
        if (name.isEmpty())
            return null;
        //look for the car with the same name. The name may be prefixed or suffixed with the car number
        //#61 Jeffrey Gilliam
        //Jeffrey Gilliam #61
        try {
            for (Iterator<Entry<Integer, iRacingCar>> itr = m_cars.entrySet().iterator(); itr.hasNext();) {
                iRacingCar car = itr.next().getValue();
                if (name.equalsIgnoreCase(car.getDriverName().getString())
                || name.equalsIgnoreCase(car.getDriverName(false).getString())
                || name.equalsIgnoreCase(String.format("#%s %s", car.getNumber().getString(),car.getDriverName().getString()))
                || name.equalsIgnoreCase(String.format("#%s %s", car.getNumber().getString(),car.getDriverName(false).getString()))
                || name.equalsIgnoreCase(String.format("%s #%s", car.getDriverName().getString(),car.getNumber().getString()))
                || name.equalsIgnoreCase(String.format("%s #%s", car.getDriverName(false).getString(),car.getNumber().getString()))
                || name.equalsIgnoreCase(String.format("%s %s", car.getNumber().getString(),car.getDriverName().getString()))
                || name.equalsIgnoreCase(String.format("%s %s", car.getNumber().getString(),car.getDriverName(false).getString()))
                || name.equalsIgnoreCase(String.format("%s %s", car.getDriverName().getString(),car.getNumber().getString()))
                || name.equalsIgnoreCase(String.format("%s %s", car.getDriverName(false).getString(),car.getNumber().getString()))
                ) {
                    return car;
                }
            }
        }
        catch (IllegalStateException e) {
            return null;
        }
        return null;
    }
    
    public boolean onDataVersionChange() {
        
        Iterator<Entry<Integer,iRacingCar>> itr = m_cars.entrySet().iterator();
        while (itr.hasNext()) {
            iRacingCar c = itr.next().getValue();
            c.onDataVersionChange();
        }
        
        return true;
    }
    
    private void _update() {
        if (!this._needsUpdating() && m_cars.size() > 0)
            return;
        
        if (m_maxCars == 0 && m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLap") != null)
            setValue(m_maxCars = m_SIMPlugin.getIODriver().getVarHeaders().getVarHeader("CarIdxLap").Count);
        
        if (m_maxCars == 0) //don't mess around. If the above logic doesn't give it to us, use 64
            setValue(m_maxCars = 64);
        
        //see if we have enough cars initialized, don't worry about too many
        if (m_cars.size() == 0) {
            m_cars.put(-2, new iRacingCar(m_SIMPlugin,-2,"PITSTALL",-1));

            for (int i=0; i < m_maxCars; i++) {
                if (!m_cars.containsKey(i))
                    m_cars.put(i, new iRacingCar(m_SIMPlugin));
            }
        }
        
        m_numberOfCars = 0;
        m_SOF          = 0;
        
        double fastestTime  = 99999.0;
        double bestTime     = 99999.9;
        int minLaps         = 0;
        int iRatingCount    = 0;
        double iRatingExp   = 0.0;
        double ln           = 1600.0 / Math.log(2);
        
        for (int driversIdx=0; driversIdx < m_maxCars; driversIdx++) {
            String sDriversIdx = Integer.toString(driversIdx);

            //the caridx is not always the same as the array index due to them hiding ghost cars and spectators
            String sCarIdx       = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"CarIdx");

            if (!sCarIdx.isEmpty()) {
                int carIdx = Integer.parseInt(sCarIdx);

                iRacingCar car = m_cars.get(carIdx);

                //as new cars pop in or it's not the same car, update the cache.
                if (!car.isValid(carIdx,driversIdx)) {  //if car has not been initialized or needs reinitializing
                    String carpath   = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"CarPath");

                    if (!carpath.isEmpty()) {    //if the car is in the session, it will have this populated
                        //String trackName = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackName");
                        car = new iRacingCar(m_SIMPlugin,carIdx,carpath,driversIdx);

                        m_cars.put(carIdx, car);
                    }
                }

                //now count the number of cars in the session and the strength of the field (SOF)
                if (car.isValid() && !car.getIsEqual("PACECAR").getBoolean() && !car.getIsSpectator().getBoolean()) {
                    String iRating = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"IRating");
                    if (!iRating.isEmpty()) {
                        m_SOF += Integer.parseInt(iRating);
                        iRatingExp += Math.exp(-Double.parseDouble(iRating) / ln);
                        iRatingCount++;
                    }
                    m_numberOfCars++;

                    if (minLaps < car.getLap().getInteger())
                        minLaps = car.getLap().getInteger();
                    
                }
            }
        }

        if (iRatingCount > 0) {
            //m_SOF = m_SOF / iRatingCount;
            
            //got this from the iRacing forum
            m_SOF = (int)Math.round(ln * Math.log((double)iRatingCount / iRatingExp));
        }

        //if it's a race
        if (m_SIMPlugin.getSession().getType().getString().equalsIgnoreCase(Session.Type.RACE))
            minLaps -= 2;  //only those up to 2 laps down will get considered for the fastest last lap.
        else
            minLaps = 0;
        
        for (Iterator<Entry<Integer, iRacingCar>> itr = m_cars.entrySet().iterator(); itr.hasNext();) {
            iRacingCar car = itr.next().getValue();
            
            if (car.isValid() && !car.getIsEqual("PACECAR").getBoolean() && !car.getIsSpectator().getBoolean()) {
                double time = car.getLapTime(iRacingCar.LapType.SESSIONLAST).getDouble();
                if (time > 0.0 && car.getLap().getInteger() >= minLaps && time < fastestTime) {
                    fastestTime   = time;
                    m_fastestCar  = car;
                }
                
                time = car.getLapTime(iRacingCar.LapType.SESSIONBEST).getDouble();
                
                if (time > 0.0 && time < bestTime) {
                    bestTime   = time;
                    m_bestCar  = car;
                }
            }
        }
        
    }
}
