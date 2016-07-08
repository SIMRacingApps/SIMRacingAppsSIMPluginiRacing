package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

public class SessionDataCarsByRelativePosition extends SessionData {
    
    SessionDataCars m_cars;
    iRacingSIMPlugin m_SIMPlugin;
    
    private ArrayList<Integer> m_positions = new ArrayList<Integer>();
    private int m_indexREFERENCE = -1;
    
    public SessionDataCarsByRelativePosition(iRacingSIMPlugin SIMPlugin, SessionDataCars cars) {
        super(SIMPlugin, "CarsByRelativePosition", -1, "integer",State.OFF);
        m_SIMPlugin = SIMPlugin;
        m_cars      = cars;
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
            //first populate a TreeMap to sort by the position in the race.
            Map<Double,iRacingCar> positionmap = new TreeMap<Double,iRacingCar>();

            for (int i=0; i < m_cars.getInteger(); i++) {
                iRacingCar c = m_cars.getCar(i);

                if (c != null && c.isValid() && c.getPosition().getInteger() > 0 && !c.getIsSpectator().getBoolean()) {
                    //double trackposition = c.LapCompleted + (c.LapPercentCompleted >= 0.0 ? c.LapPercentCompleted : 0.0);
                    //positionmap.put(trackposition, c.getId());
                    positionmap.put(c.getPosition().getDouble(), c);
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
