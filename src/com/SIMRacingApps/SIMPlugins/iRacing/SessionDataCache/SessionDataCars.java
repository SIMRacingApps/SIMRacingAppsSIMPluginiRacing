package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Session;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2021 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SessionDataCars extends SessionData {

    private int m_maxCars = 64;
    private Map<Integer,iRacingCar> m_cars = new HashMap<Integer,iRacingCar>();
    private int m_numberOfCars = 0;
    private double m_SOF = 0;
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
        return (int)Math.floor(m_SOF + 0.5); 
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

    //make static so iRacingCar class can call it.
    public static boolean _isMatching( iRacingCar car, String name, String nameMapped) {
        String name2 = name.replaceAll("#\\d[ \t-_=]", "").replaceAll("[\\-_=\\*<>]", "").replaceAll("\\d.?$", "").trim(); //remove punctuation and trailing numbers
        String nameMapped2 = nameMapped.replaceAll("#\\d[ \\t-_=]", "").replaceAll("[\\-_=\\*<>]", "").replaceAll("\\d.?$", "").trim(); //remove punctuation and trailing numbers
        
        String driverName = car.getDriverName(true).getString().replaceAll("[\\-_=\\*<>]", "").replaceAll("\\d.?$", ""); //remove punctuation and trailing numbers
        String driverNameNotMapped = car.getDriverName(false).getString().replaceAll("[\\-_=\\*<>]", "").replaceAll("\\d.?$", ""); //remove punctuation and trailing numbers
        String driverNameWithNumber = car.getDriverName(false).getString().replaceAll("[\\-_=\\*<>]", ""); //remove punctuation leave the number

        if (name2.equalsIgnoreCase(driverNameNotMapped)
        ||  name2.equalsIgnoreCase(driverName)
        ||  name2.equalsIgnoreCase(driverNameWithNumber)
        ||  nameMapped2.equalsIgnoreCase(driverNameNotMapped)
        ||  nameMapped2.equalsIgnoreCase(driverName)
        ||  nameMapped2.equalsIgnoreCase(driverNameWithNumber)
//        if (name.equalsIgnoreCase(car.getDriverName().getString())
//        || name.equalsIgnoreCase(car.getDriverName(false).getString())
//        || name.equalsIgnoreCase(String.format("#%s %s", car.getNumber().getString(),car.getDriverName().getString()))
//        || name.equalsIgnoreCase(String.format("#%s %s", car.getNumber().getString(),car.getDriverName(false).getString()))
//        || name.equalsIgnoreCase(String.format("%s #%s", car.getDriverName().getString(),car.getNumber().getString()))
//        || name.equalsIgnoreCase(String.format("%s #%s", car.getDriverName(false).getString(),car.getNumber().getString()))
//        || name.equalsIgnoreCase(String.format("%s %s", car.getNumber().getString(),car.getDriverName().getString()))
//        || name.equalsIgnoreCase(String.format("%s %s", car.getNumber().getString(),car.getDriverName(false).getString()))
//        || name.equalsIgnoreCase(String.format("%s %s", car.getDriverName().getString(),car.getNumber().getString()))
//        || name.equalsIgnoreCase(String.format("%s %s", car.getDriverName(false).getString(),car.getNumber().getString()))
        ) {
            return true;
        }
        return false;
    }
    
    public iRacingCar getByName(String name) {
        if (name.isEmpty())
            return null;
        
        //look for the car with the same name. The name may be prefixed or suffixed with the car number
        //#61 Jeffrey Gilliam
        //Jeffrey Gilliam #61
        String nameToMatch = name.replaceAll("^#\\d.?[\\s\\-_=]|^\\d.?[\\s\\-_=]", ""); //remove number from beginning
        nameToMatch = nameToMatch.replaceAll("[\\s\\-_=]#\\d.?$|[\\s\\-_=]\\d.?$", ""); //remove number from the end
        nameToMatch = nameToMatch.replaceAll("[\\-_=\\*<>]", ""); //remove punctuation
        nameToMatch = nameToMatch.trim(); //now remove any leading/trailing spaces
        
        //now see if this name has a mapping
        String nameToMatchMapped = Server.getArg(nameToMatch,nameToMatch);
        
        try {
            for (Iterator<Entry<Integer, iRacingCar>> itr = m_cars.entrySet().iterator(); itr.hasNext();) {
                iRacingCar car = itr.next().getValue();
                if (_isMatching(car,nameToMatch,nameToMatchMapped))
                    return car;
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
        
        //things to track by class name in a multiclass race.
        class ClassName {
            String name = "";
            int didNotStart = 0;
            double changeStartersSum = 0.0;
            double expectedScoreNonStartersSum = 0.0;
            int iRatingCount    = 0;
            double iRatingExp   = 0.0;
            double SOF = 0.0;
        }
        Map<String,ClassName> byClass = new HashMap<String,ClassName>();
        
        String classOverride = "";
        
        for (int driversIdx=0; driversIdx < m_maxCars; driversIdx++) {
            String sDriversIdx = Integer.toString(driversIdx);

            //the caridx is not always the same as the array index due to them hiding ghost cars and spectators
            String sCarIdx       = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"CarIdx");

            if (!sCarIdx.isEmpty()) {
                int carIdx = Integer.parseInt(sCarIdx);

                iRacingCar car = m_cars.get(carIdx);

                String className = !classOverride.isEmpty() ? classOverride : car.getClassName().getString();
                if (!byClass.containsKey(className)) {
                    byClass.put(className, new ClassName());
                    byClass.get(className).name = className;
                }

                //as new cars pop in or it's not the same car, update the cache.
                if (!car.isValid(carIdx,driversIdx)) {  //if car has not been initialized or needs reinitializing
                    String carpath   = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"CarPath");

                    if (!carpath.isEmpty()) {    //if the car is in the session, it will have this populated
                        //String trackName = m_SIMPlugin.getIODriver().getSessionInfo().getString("WeekendInfo","TrackName");
                        car = new iRacingCar(m_SIMPlugin,carIdx,carpath,driversIdx);

                        m_cars.put(carIdx, car);
                        className = !classOverride.isEmpty() ? classOverride : car.getClassName().getString();
                        if (!byClass.containsKey(className)) {
                            byClass.put(className, new ClassName());
                            byClass.get(className).name = className;
                        }
                    }
                }

                //now count the number of cars in the session and the strength of the field (SOF)
                if (car.isValid() && !car.getIsEqual("PACECAR").getBoolean() && !car.getIsSpectator().getBoolean()) {
                    String iRating = m_SIMPlugin.getIODriver().getSessionInfo().getString("DriverInfo","Drivers",sDriversIdx,"IRating");
                    if (!iRating.isEmpty()) {
                        car.m_dynamicIRating.m_iRating = Double.parseDouble(iRating); 
                        m_SOF += car.m_dynamicIRating.m_iRating;
                        byClass.get(className).SOF += car.m_dynamicIRating.m_iRating;
                        //from cell BR1
                        car.m_dynamicIRating.m_iRatingExp = Math.exp(-car.m_dynamicIRating.m_iRating / ln);
                        //from cell BS3:BSxx
                        //iRatingExp += Math.exp(-Double.parseDouble(iRating) / ln);
                        iRatingExp += car.m_dynamicIRating.m_iRatingExp;
                        byClass.get(className).iRatingExp += car.m_dynamicIRating.m_iRatingExp;
                        iRatingCount++;
                        byClass.get(className).iRatingCount++;
                    }
                    m_numberOfCars++;

                    if (minLaps < car.getLap().getInteger())
                        minLaps = car.getLap().getInteger();
                    
                }
            }
        }

        if (iRatingCount > 0) {
            //old way was a simple average
            //m_SOF = m_SOF / iRatingCount;
            
            //got this from the iRacing forum
            m_SOF = (int)Math.round(ln * Math.log((double)iRatingCount / iRatingExp));
            Server.logger().finest(String.format("Class[%s] SOF=%f, Count=%d, exp=%f", "ALL", m_SOF, iRatingCount, iRatingExp));
        }

        for (Iterator<Entry<String, ClassName>> itr = byClass.entrySet().iterator(); itr.hasNext();) {
            ClassName c = itr.next().getValue();
            c.SOF = (int)Math.round(ln * Math.log((double)c.iRatingCount / c.iRatingExp));
            Server.logger().finest(String.format("Class[%s] SOF=%f, Count=%d, exp=%f", c.name, c.SOF, c.iRatingCount, c.iRatingExp));
        }
        
        
        //if it's a race
        boolean isRace = m_SIMPlugin.getSession().getType().getString().equalsIgnoreCase(Session.Type.RACE); 
        if (isRace)
            minLaps -= 2;  //only those up to 2 laps down will get considered for the fastest last lap.
        else
            minLaps = 0;
        
        
        for (Iterator<Entry<Integer, iRacingCar>> itr = m_cars.entrySet().iterator(); itr.hasNext();) {
            iRacingCar car = itr.next().getValue();

//if (car.getId().getInteger() == 16)
//    car = car;
            if (car.isValid() && !car.getIsEqual("PITSTALL").getBoolean() && !car.getIsEqual("PACECAR").getBoolean() && !car.getIsSpectator().getBoolean()) {
                String className = !classOverride.isEmpty() ? classOverride : car.getClassName().getString();
                int position = classOverride.isEmpty() ? car.getPositionClass().getInteger() : car.getPositionClass().getInteger();
                
                if (isRace) {
                    car.m_dynamicIRating.m_expectedScore = 0.0;
                    for (Iterator<Entry<Integer, iRacingCar>> itr2 = m_cars.entrySet().iterator(); itr2.hasNext();) {
                        iRacingCar car2 = itr2.next().getValue();
                        if (car2.isValid() && !car2.getIsEqual("PITSTALL").getBoolean() && !car2.getIsEqual("PACECAR").getBoolean() && !car2.getIsSpectator().getBoolean()) {
                            String className2 = !classOverride.isEmpty() ? classOverride : car.getClassName().getString();
                            
                            //calculate the score only if in the same class
                            if (className.equals(className2)) {
                                //From cell AA:3   
                                //             (1   - EXP(-$B3/$BR$1))                   * EXP(-AA$2/$BR$1)                   / ( (1   - EXP(-AA$2/$BR$1))                   * EXP(-$B3/$BR$1)                   + (1   - EXP(-$B3/$BR$1))                   * EXP(-AA$2/$BR$1))
                                double score = (1.0 - car.m_dynamicIRating.m_iRatingExp) * car2.m_dynamicIRating.m_iRatingExp / ( (1.0 - car2.m_dynamicIRating.m_iRatingExp) * car.m_dynamicIRating.m_iRatingExp + (1.0 - car.m_dynamicIRating.m_iRatingExp) * car2.m_dynamicIRating.m_iRatingExp);
                                car.m_dynamicIRating.m_expectedScore += score;
                            }
                        }
                    }
                    
                    //SUM(AA3:BQ3)-0.5
                    car.m_dynamicIRating.m_expectedScore -= 0.5;
                    
                    //until at least 1 lap is completed, they are considered as did not start
                    if (false && car.getLap(Car.LapType.COMPLETED).getInteger() < 1) {
                        byClass.get(className).didNotStart++;
                        car.m_dynamicIRating.m_fudgeFactor = 0.0;
                        car.m_dynamicIRating.m_changeStarters = 0.0;
                        car.m_dynamicIRating.m_expectedScoreNonStarter = car.m_dynamicIRating.m_expectedScore;
                        
                        byClass.get(className).expectedScoreNonStartersSum += car.m_dynamicIRating.m_expectedScoreNonStarter;
                        
                    }
                    else {
                        //                                   (($BS$1                               - ($BT$1/2))                              / 2 - A3      ) / 100
                        car.m_dynamicIRating.m_fudgeFactor = ((byClass.get(className).iRatingCount - (byClass.get(className).didNotStart/2)) / 2 - position) / 100.0;
                        
                        //                                      ($BS$1                               - A3       - BS3                                  - BT3                               ) * 200 / ($BS$1                               - $BT$1      )
                        car.m_dynamicIRating.m_changeStarters = (byClass.get(className).iRatingCount - position - car.m_dynamicIRating.m_expectedScore - car.m_dynamicIRating.m_fudgeFactor) * 200 / (byClass.get(className).iRatingCount - byClass.get(className).didNotStart);
    
                        byClass.get(className).changeStartersSum += car.m_dynamicIRating.m_changeStarters;
                        
                        car.m_dynamicIRating.m_expectedScoreNonStarter = 0.0;
                    }
                }
                
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
        
        if (isRace) {
            for (Iterator<Entry<Integer, iRacingCar>> itr = m_cars.entrySet().iterator(); itr.hasNext();) {
                iRacingCar car = itr.next().getValue();
                if (car.isValid() && !car.getIsEqual("PITSTALL").getBoolean() && !car.getIsEqual("PACECAR").getBoolean() && !car.getIsSpectator().getBoolean()) {
                    String className = !classOverride.isEmpty() ? classOverride : car.getClassName().getString();
                    
                    if (byClass.get(className).didNotStart > 0 && byClass.get(className).expectedScoreNonStartersSum > 0.0)
                        //                                         -SUM(BU$3:BU$45)                          / BT$1                               * BV3                                                                   / AVERAGE(BV$3:BV$45)
                        car.m_dynamicIRating.m_changeNonStarters = -byClass.get(className).changeStartersSum / byClass.get(className).didNotStart * car.m_dynamicIRating.m_expectedScoreNonStarter / (byClass.get(className).expectedScoreNonStartersSum/byClass.get(className).didNotStart);
                    else
                        car.m_dynamicIRating.m_changeNonStarters = 0.0;
                    
                    if (false && car.getLap(Car.LapType.COMPLETED).getInteger() < 1) {
                        car.m_dynamicIRating.m_change = car.m_dynamicIRating.m_changeNonStarters;
//                        car.m_dynamicIRating.m_points = 0.0;
                    }
                    else {
                        car.m_dynamicIRating.m_change = car.m_dynamicIRating.m_changeStarters;
                    
//                        //IF($B4="",0.5*1.06*B$1/16,($BS$1/($BS$1+1)*1.06*B$1/16*($BS$1-$A3)/($BS$1-1)))
//                        String carIdentifier = "P";
//                        Integer position = car.getPosition().getInteger() + 1;
//                        carIdentifier.concat(position.toString());
//                        
//                        //if there is a car in the position behind this one and there's more than one car (avoid divide by zero)
//                        if (m_SIMPlugin.getSession().getCar(carIdentifier).getId().getInteger() != -1 && iRatingCount > 1) {
//                            //                              ($BS$1        / ($BS$1        + 1) * 1.06 * B$1   / 16   * ($BS$1        - $A3         ) / ($BS$1        - 1))
//                            car.m_dynamicIRating.m_points = (iRatingCount / (iRatingCount + 1) * 1.06 * byClass.get(className).SOF / 16.0 * (iRatingCount - position - 1) / (iRatingCount - 1));
//                        }
//                        else {
//                            //                              0.5 * 1.06 * B$1   / 16
//                            car.m_dynamicIRating.m_points = 0.5 * 1.06 * byClass.get(className).SOF / 16.0;
//                        }
                    }
                    
                    if (byClass.get(className).iRatingCount > 1)
                        car.m_dynamicIRating.m_newIRating = (int)Math.floor(car.m_dynamicIRating.m_iRating + car.m_dynamicIRating.m_change + 0.5);
                    else
                        car.m_dynamicIRating.m_newIRating = car.m_dynamicIRating.m_iRating;
                }
            }
        }
    }
}
