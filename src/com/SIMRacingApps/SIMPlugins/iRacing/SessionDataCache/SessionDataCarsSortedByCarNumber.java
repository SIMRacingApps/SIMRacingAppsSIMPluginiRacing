package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2021 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class SessionDataCarsSortedByCarNumber extends SessionData {
    
    SessionDataCars m_cars;
    iRacingSIMPlugin m_SIMPlugin;
    
    private Map<Integer,Integer> m_carNumberIdx = new TreeMap<Integer,Integer>();
    private ArrayList<Integer>  m_carNumbers   = new ArrayList<Integer>();
    
    public SessionDataCarsSortedByCarNumber(iRacingSIMPlugin SIMPlugin, SessionDataCars cars) {
        super(SIMPlugin, "CarsOrderedByCarNumber", "", "string",State.OFF);
        m_SIMPlugin = SIMPlugin;
        m_cars      = cars;
    }

    public int getCarIdx(int carOrderIdx) {
        this._update();
        try {
            setValue(m_carNumbers.get(carOrderIdx),"integer",State.NORMAL);
            return getInteger();
        }
        catch (IndexOutOfBoundsException e) {}
        return -1;
    }

    private void _update() {
        if (this._needsUpdating()) {
            m_carNumberIdx = new TreeMap<Integer,Integer>();
            for (int caridx=0; caridx < m_cars.getInteger(); caridx++) {
                iRacingCar car = m_cars.getCar(caridx); 
                if (car != null && !car.getIsEqual("PACECAR").getBoolean() && !car.getIsSpectator().getBoolean()) {
                    String number = car.getNumber().getString();
                    int length    = number.length();
                    while (number.length() < 3)
                        number = "0" + number;
                    number = "1" + number + Integer.toString(length);
                    try {
                        m_carNumberIdx.put(Integer.parseInt(number), caridx);
                    }
                    catch (NumberFormatException e) {}
                }
            }
            m_carNumbers = new ArrayList<Integer>();
            Iterator<Entry<Integer, Integer>> itr = m_carNumberIdx.entrySet().iterator();
            while (itr.hasNext()) {
                m_carNumbers.add(itr.next().getValue());
            }
        }
    }
}
