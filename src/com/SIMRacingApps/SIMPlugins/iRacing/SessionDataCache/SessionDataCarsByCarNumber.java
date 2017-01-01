package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.HashMap;
import java.util.Map;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SessionDataCarsByCarNumber extends SessionData {
    private static final long serialVersionUID = 3286797859044221296L;
    
    SessionDataCars m_cars;
    iRacingSIMPlugin m_SIMPlugin;
    
    private Map<String,Integer> m_carNumberIdx = new HashMap<String,Integer>();
    
    public SessionDataCarsByCarNumber(iRacingSIMPlugin SIMPlugin, SessionDataCars cars) {
        super(SIMPlugin, "CarsByCarNumber", "", "string",State.OFF);
        m_SIMPlugin = SIMPlugin;
        m_cars      = cars;
    }

    public int getCarIdx(String carNumber) {
        this._update();
        if (m_carNumberIdx.containsKey(carNumber)) {
            setValue(m_carNumberIdx.get(carNumber),"integer",State.NORMAL);
            return getInteger();
        }
        return -1;
    }

    private void _update() {
        if (this._needsUpdating()) {
            m_carNumberIdx = new HashMap<String,Integer>();
            for (int caridx=0; caridx < m_cars.getInteger(); caridx++) {
                iRacingCar car = m_cars.getCar(caridx);
                if (car != null && !car.getIsSpectator().getBoolean()) {
                    String number = m_cars.getCar(caridx).getNumber().getString();
                    m_carNumberIdx.put(number, caridx);
                }
            }
        }
    }
}
