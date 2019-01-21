package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2019 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SessionDataCarsByRelativePositionClass extends SessionData {
    
    SessionDataCars m_cars;
    iRacingSIMPlugin m_SIMPlugin;
    
    private ArrayList<Integer> m_positions = new ArrayList<Integer>();
    private int m_indexREFERENCE = -1;
    
    public SessionDataCarsByRelativePositionClass(iRacingSIMPlugin SIMPlugin, SessionDataCars cars) {
        super(SIMPlugin, "CarsByRelativePositionClass", -1, "integer",State.OFF);
        m_SIMPlugin = SIMPlugin;
        m_cars      = cars;
    }

    private int m_dataVersion = -1;
    protected boolean _needsUpdating() {
        boolean b = super._needsUpdating();
        
        if (!b 
        &&  m_SIMPlugin.getIODriver().getVars().getBoolean("IsReplayPlaying")
        &&  m_SIMPlugin.isConnected()
        &&  this.m_dataVersion != m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick()
        ) {
            b = true;
            this.m_dataVersion = m_SIMPlugin.getIODriver().getHeader().getLatest_VarBufTick();
        }

        return b;
    }
    
    public int getCarIdx(int position) {
        this._update();
        setValue(-1,"integer",State.OFF);
        if (m_indexREFERENCE > -1) {
            try {
                setValue(m_positions.get(m_indexREFERENCE - position),"integer",State.NORMAL);
            }
            catch (IndexOutOfBoundsException e) {}
        }
        return getInteger();
    }

    private void _update() {
        if (this._needsUpdating()) {
            iRacingCar c = m_cars.getCar(m_SIMPlugin.getSession().getCar("REFERENCE").getId().getInteger());
            String referenceClassName = c != null ? c.getClassName().getString() : "";
            //first populate a TreeMap to sort by the position in the race.
            Map<Double,iRacingCar> positionmap = new TreeMap<Double,iRacingCar>();

            for (int i=0; i < m_cars.getInteger(); i++) {
                c = m_cars.getCar(i);

                if (c != null 
                &&  c.isValid()
                &&  !c.getIsSpectator().getBoolean()
                &&  c.getPositionClass().getInteger() > 0 
                &&  c.getClassName().getString().equals(referenceClassName)
                ) {
                    positionmap.put(c.getPositionClass().getDouble(), c);
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
