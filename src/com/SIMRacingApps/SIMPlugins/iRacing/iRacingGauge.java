/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing;

import java.util.Map;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Gauge;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.SIMPlugins.iRacing.VarHeaders.VarHeader;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class iRacingGauge extends Gauge {

    protected IODriver m_IODriver;
    protected String m_varName;
    protected VarHeader m_varHeader;
    protected String m_iRacingUOM;
    protected boolean m_updateSIM;
    protected boolean m_isFixedSetup;
    
    /**
     * This is the generic var reader. It will utilize the car's id to read arrays.
     * Any special needs readers will inherit from this class and override.
     * 
     * @param type The type of gauge
     * @param car The car
     * @param track The track
     * @param IODriver The iRacing driver to use
     * @param varName The name of the var
     * @param defaultUOM The UOM of the var in case iRacing doesn't define it.
     */
    public iRacingGauge(String type, iRacingCar car, Track track, IODriver IODriver, String varName, String defaultUOM, Map<String, Map<String, Map<String, Object>>> simGauges) {
        super(type, car, track, simGauges);
        
        m_IODriver = IODriver;
        m_varName = varName;
        m_varHeader = IODriver.getVarHeaders().getVarHeader(varName);
        m_iRacingUOM = defaultUOM;
        if (m_varHeader != null) {
            if (!m_varHeader.Unit.isEmpty())
                m_iRacingUOM = m_varHeader.Unit;
        }

        m_updateSIM = false;
        
        try {
            //Override the JSON file based on what iRacing says
            //if we are in fixed mode, set the gauges that cannot be changed to true.
            String fixed = m_IODriver.getSessionInfo().getString("WeekendInfo","WeekendOptions","IsFixedSetup");
            
            m_isFixedSetup = (Integer.parseInt(fixed) == 1);
//TODO: Create classes for gauges that are initially not fixed according to the JSON definition to use this setting to override it.            
//            && (
//                    m_varName.equals(Gauge.Type.TIREPRESSURELF)
//               ||   m_varName.equals(Gauge.Type.TIREPRESSURELR)
//               ||   m_varName.equals(Gauge.Type.TIREPRESSURERF)
//               ||   m_varName.equals(Gauge.Type.TIREPRESSURERR)
//                    //TODO: gauges we can't change via send keys (wedge,tape,brake bias), set to false as well
//                    //NOTE: for now setting these to fixed will simply tell any clients they can't change the setup remotely
//                    //      Therefore, the client can use this to disable/enable the feature.
//                    //      It doesn't mean you can't change it through the SIM.
////                    this._getGauge(Gauge.Type.RRWEDGEADJUSTMENT)  .setIsFixed(true);
////                    this._getGauge(Gauge.Type.BRAKEBIASADJUSTMENT).setIsFixed(true);
////                    this._getGauge(Gauge.Type.TAPE)               .setIsFixed(true);
////                    this._getGauge(Gauge.Type.ABS)                .setIsFixed(true);
////                    this._getGauge(Gauge.Type.TRACTIONCONTROL)    .setIsFixed(true);
////                    this._getGauge(Gauge.Type.ANTIROLLFRONT)      .setIsFixed(true);
////                    this._getGauge(Gauge.Type.ANTIROLLREAR)       .setIsFixed(true);
////                    this._getGauge(Gauge.Type.FUELMIXTURE)        .setIsFixed(true);
////                    this._getGauge(Gauge.Type.WEIGHTJACKERLEFT)   .setIsFixed(true);
////                    this._getGauge(Gauge.Type.WEIGHTJACKERRIGHT)  .setIsFixed(true);
//               )
//            ) {
//                _setIsFixed(true);
//            }
        } catch (NumberFormatException e) {
            m_isFixedSetup = false;
        }
    }

    @Override
    public Data getValueCurrent(String UOM) { 
        Data d = super.getValueCurrent(UOM);
        d = _readVar();
        return _getReturnValue(d,UOM);
    }
    
    public void onDataVersionChange(int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        //users can change this during a session, so keep it up to date here
        m_measurementSystem = m_IODriver.getVars().getInteger("DisplayUnits") == 1 ? "METRIC" : "IMPERIAL";
    }   
    
    //returns true if the SIM needs to be updated with the requested changes.
    //only gauges that we can control should set this to true.
    public boolean _getUpdateSIM() { return m_updateSIM; }
    
    //utility method to read the var and return the value with UOM and States applied
    //only use the value if the state is NORMAL
    protected Data _readVar() {
        Data d = new Data(m_varName);
        double value = d.getDouble();
        
        if (m_varHeader == null)
            d.setState(Data.State.NOTAVAILABLE);
        else
        if (m_car.isValid()) {
            if (!m_varName.isEmpty()){
                try {
                    //if this var is an array, use the id as the index
                    if (m_varHeader.Count > 1)
                        value = m_IODriver.getVars().getDouble(m_varName,m_car.getId().getInteger());
                    else
                        value = m_IODriver.getVars().getDouble(m_varName);
                    
                    if (Double.isNaN(value))
                        d.setState(Data.State.OFF);
                    else {
                        //Now normalize all the values that are percentages to be times 100
                        //except brake bias which already is normalized.
                        if (m_iRacingUOM.equals("%") && !m_varName.equals("dcBrakeBias"))
                            value *= 100.0;
                        
                        d.setValue(value,m_iRacingUOM,Data.State.NORMAL);
                    }
                    
                } catch (IndexOutOfBoundsException e) {
                    //set the value to the var name and return it as an error state
                    //can happen if the id is -1
                    d.setValue(m_varName);
                    d.setState(Data.State.ERROR);
                    return d;
                }
            }
        }
        else {
            d.setState(Data.State.OFF);
        }
        
        return d;
    }
}
