package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.HashMap;
import java.util.Map;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2023 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SessionDataCarsByPosition extends SessionData {
    
    SessionDataCars m_cars;
    iRacingSIMPlugin m_SIMPlugin;
    
    private Map<Integer,Integer> m_positions = new HashMap<Integer,Integer>();
    
    public SessionDataCarsByPosition(iRacingSIMPlugin SIMPlugin, SessionDataCars cars) {
        super(SIMPlugin, "CarsByPosition", -1, "integer",State.OFF);
        m_SIMPlugin = SIMPlugin;
        m_cars      = cars;
    }

    public int getCarIdx(int position) {
        this._update();
        if (m_positions.containsKey(position)) {
            setValue(m_positions.get(position),"integer",State.NORMAL);
            return getInteger();
        }
        return -1;
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
    
    private void _update() {
        if (this._needsUpdating()) {
            m_positions = new HashMap<Integer,Integer>();
            for (int caridx=0; caridx < m_cars.getInteger(); caridx++) {
                iRacingCar car = m_cars.getCar(caridx);
                if (car != null && !car.getIsSpectator().getBoolean()) {
                    int pos = car.getPosition().getInteger();
                    if (pos > 0)
                        m_positions.put(pos,caridx);
                }
            }
        }
    }
}
