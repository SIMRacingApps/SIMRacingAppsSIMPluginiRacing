package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2019 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SessionDataCarsByRelativeLocation extends SessionData {
    SessionDataCars m_cars;
    iRacingSIMPlugin m_SIMPlugin;
    
    private ArrayList<Integer> m_positions = new ArrayList<Integer>();
    private int m_indexREFERENCE = -1;
    
    public SessionDataCarsByRelativeLocation(iRacingSIMPlugin SIMPlugin, SessionDataCars cars) {
        super(SIMPlugin, "CarsByRelativeLocation", -1, "integer",State.OFF);
        m_SIMPlugin = SIMPlugin;
        m_cars      = cars;
    }

    public int getCarIdx(int position) {
        this._update();
        setValue(-1,"integer",State.OFF);
        if (m_indexREFERENCE > -1) {
            try {
                setValue(m_positions.get(m_indexREFERENCE + position),"integer",State.NORMAL);
            }
            catch (IndexOutOfBoundsException e) {}
        }
        return getInteger();
    }

    private int m_dataVersion = -1;
    protected boolean _needsUpdating() {
        boolean b = super._needsUpdating();
        
        if (!b 
        &&  m_SIMPlugin.isConnected()
        &&  this.m_dataVersion != m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
        ) {
            b = true;
            this.m_dataVersion = m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick();
        }

        return b;
    }
    
    private void _update() {
        if (this._needsUpdating()) {
            double refpct = m_SIMPlugin.getSession().getCar("REFERENCE").getLap(iRacingCar.LapType.COMPLETEDPERCENT).getDouble() / 100.0;
            double refpctbottom = refpct - 0.5;
            double refpcttop    = refpct + 0.5;

            Data refstatus = m_SIMPlugin.getSession().getCar("REFERENCE").getStatus();
            String reflocation = "INVALID";
            
            //when entering or leaving pits show relative to on track cars.
            if (refstatus.getString().contains("TRACK") 
            || refstatus.getString().equals(Car.Status.LEAVINGPITS)
            || refstatus.getString().equals(Car.Status.APPROACHINGPITS)
            ) {
                reflocation = "ONTRACK";
            }
            else
            if (refstatus.getString().contains("PIT")) {
                reflocation = "ONPITROAD";
            }

            //first populate a TreeMap to sort by the position in the race.
            Map<Double,iRacingCar> positionmap = new TreeMap<Double,iRacingCar>();

            for (int i=0; i < m_cars.getInteger(); i++) {
                iRacingCar c = m_cars.getCar(i);
                String location = "INVALID";
                if (c != null 
                && (c.getStatus().getString().contains("TRACK")  
                    || c.getStatus().getString().equals(Car.Status.LEAVINGPITS)
                    || c.getStatus().getString().equals(Car.Status.APPROACHINGPITS)
                    )
                ) {
                    location = "ONTRACK";
                }
                else
                if (c != null && c.getStatus().getString().contains("PIT")) {
                    location = "ONPITROAD";
                }

                if (c != null 
                && c.isValid()
//                &&  !c.getIsSpectator().getBoolean()
                &&  c.getLap(iRacingCar.LapType.COMPLETEDPERCENT).getDouble() >= 0.0
                &&  m_SIMPlugin.getIODriver().getVars().getInteger("CarIdxLap", c.getId().getInteger()) != -1
                &&  !c.getStatus().getString().equals(iRacingCar.Status.INVALID)
                &&  !c.getStatus().getString().equals(iRacingCar.Status.INGARAGE)
                &&  ((!c.getIsEqual("PACECAR").getBoolean() && reflocation.equals(location)) || (c.getIsEqual("PACECAR").getBoolean() && !c.getStatus().getString().contains("PIT")))
                ) {
                    double trackposition = c.getLap(iRacingCar.LapType.COMPLETEDPERCENT).getDouble() / 100.0;
                    if (trackposition >= refpctbottom && trackposition <= refpcttop)
                        positionmap.put(trackposition, c);

                    //if not refcar, add everyone a lap ahead and a lap behind
                    //if (m_SIMPlugin.getSession().getCar("REFERENCE").getId().getInteger() != i) {
                    if (!c.getIsEqual("REFERENCE").getBoolean()) {
                        if ((trackposition - 1.0) >= refpctbottom && (trackposition - 1.0) <= refpcttop)
                            positionmap.put(trackposition - 1.0, c);
                        if ((trackposition + 1.0) >= refpctbottom && (trackposition + 1.0) <= refpcttop)
                            positionmap.put(trackposition + 1.0, c);
                    }
                }
            }

            //now copy these to an indexable array
            m_positions = new ArrayList<Integer>();
            m_indexREFERENCE = -1;
            for (Entry<Double,iRacingCar> entry : positionmap.entrySet()) {
                if (entry.getValue().getIsEqual("REFERENCE").getBoolean())
                    m_indexREFERENCE = m_positions.size();
                m_positions.add(entry.getValue().getId().getInteger());
            }
        }
    }
}
