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
import com.SIMRacingApps.Util.State;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class iRacingGauge extends Gauge {

    private   double    m_updateSIMTimestamp;   //set this to the session time to send to the SIM. Doubles as the change flag
    private   int       m_updateSIMValue;       //the value to update the SIM with. Can only send integers
    
    protected IODriver  m_IODriver;             //pointer to the driver
    protected String    m_varName;              //the name of the var to read
    protected VarHeader m_varHeader;            //the header for the var
    protected String    m_iRacingUOM;           //the UOM of the var
    protected int       m_currentLap;           //tracks the current lap
    protected double    m_sessionTime;          //tracks the current session time

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
     * @param simGaugesBefore A map that contains gauge data from the SIM to be applied first. The files can then override those values if needed.
     * @param simGaugesAfter A map that contains gauge data from the SIM to be applied after the files are processed to override any values in them.
     */
    public iRacingGauge(String type, iRacingCar car, Track track, IODriver IODriver, String varName, String defaultUOM, 
            Map<String, Map<String, Map<String, Object>>> simGaugesBefore, 
            Map<String, Map<String, Map<String, Object>>> simGaugesAfter
    ) {
        super(type, car, track, simGaugesBefore, simGaugesAfter);
        
        m_IODriver              = IODriver;
        m_varName               = varName;
        m_varHeader             = IODriver.getVarHeaders().getVarHeader(varName);
        m_iRacingUOM            = m_varHeader != null && !m_varHeader.Unit.isEmpty() ? m_varHeader.Unit : defaultUOM;
        m_updateSIMTimestamp    = 0.0;
        m_currentLap            = 1;
        
        try {
            //Override the JSON file based on what iRacing says
            //if we are in fixed mode, set the gauges that cannot be changed to true.
            String fixed = m_IODriver.getSessionInfo().getString("WeekendInfo","WeekendOptions","IsFixedSetup");
            
            m_isFixed = (Integer.parseInt(fixed) == 1);
        } catch (NumberFormatException e) {
            m_isFixed = false;
        }
    }

    public Data getValueCurrent(String UOM,String gear,String power) { 
        Data d = super.getValueCurrent(UOM);
        d.set(_readVar());
        return _getReturnValue(d,UOM,gear,power);
    }
    
    @Override
    public Data getValueCurrent(String UOM) { 
        Data d = super.getValueCurrent(UOM);
        d.set(_readVar());
        return _getReturnValue(d,UOM);
    }
    
    @Override
    public Data getLaps() {
        return getLaps(m_currentLap);
    }
    
    public void _onDataVersionChange(State status, int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        //users can change this during a session, so keep it up to date here
        m_measurementSystem = m_IODriver.getVars().getInteger("DisplayUnits") == 1 ? "METRIC" : "IMPERIAL";
        m_currentLap        = currentLap;
        m_sessionTime       = sessionTime;
    }   
    
    //returns the time this gauge value or change flag was set
    //only gauges that we can control the SIM should set this.
    public double _getSIMCommandTimestamp() { 
        return m_updateSIMTimestamp;
    }
    public int _getSIMCommandValue() { 
        return m_updateSIMValue;
    }
    
    //call this if a command is pending to be sent to the SIM
    //flag is to check or uncheck the change
    //the value needs to be in the m_iRacingUOM unit of measure
    public void _setSIMCommandTimestamp(boolean flag,double value) {
        m_updateSIMTimestamp = flag ? m_sessionTime : -m_sessionTime;
        m_updateSIMValue = (int)Math.round(value);
    }
    
    //call this once the commands have been sent to the SIM
    public void _clearSIMCommandTimestamp() {
        m_updateSIMTimestamp = 0;
    }
    
    public void _resetDetected() {
        m_changeFlag = true;
    }
    
    //utility method to read the var and return the value with UOM and States applied
    //only use the value if the state is NORMAL
    protected Data _readVar(String varName) {
        Data d = new Data(varName,0.0,"",Data.State.NOTAVAILABLE);
        double value = d.getDouble();
        
        //need to report NOTAVAILABLE if the SIM is not running or 
        //this car doesn't have this gauge
        if (m_varHeader == null || !m_IODriver.isConnected()) {
            d.setState(Data.State.NOTAVAILABLE);
        }
        else
        if (m_car.isValid()) {
            if (!varName.isEmpty()){
                try {
                    //if this var is an array, use the id as the index
                    if (m_varHeader.Count > 1)
                        value = m_IODriver.getVars().getDouble(varName,m_car.getId().getInteger());
                    else
                        value = m_IODriver.getVars().getDouble(varName);
                    
                    if (Double.isNaN(value))
                        d.setState(Data.State.OFF);
                    else {
                        if (varName.equals("dpQtape")) {
                            if (m_reader.equals("DataVarTape")) {
                                //prior to april 22, 2014 the tape was simply values from 0 to 10
                                //after that, 0 - 100
                                if (!m_IODriver.build_april_22_2014())
                                    value *= 10.0;
                            }
                            else
                            if (m_reader.equals("DataVarTape4")) {
                                if (value == 400.0 || value == 4.0)
                                    value = 100.0;
                                else
                                if (value == 300.0 || value == 3.0)
                                    value = 75.0;
                                else
                                if (value == 200.0 || value == 2.0)
                                    value = 50.0;
                                else
                                if (value == 100.0 || value == 1.0)
                                    value = 25.0;
                                else
                                    value = 0.0;
                            }
                        }
                        else
                        //Now normalize all the values that are percentages to be times 100
                        //except those that are already normalized.
                        if (m_iRacingUOM.equals("%") 
                        && !varName.equals("dcBrakeBias")
                        ) {
                            value *= 100.0;
                        }
                        
                        d.setValue(value,m_iRacingUOM,Data.State.NORMAL);
                    }
                    
                } catch (IndexOutOfBoundsException e) {
                    //set the value to the var name and return it as an error state
                    //can happen if the id is -1
                    d.setValue(varName);
                    d.setState(Data.State.ERROR);
                    return d;
                }
            }
        }
        else {
            d.setState(Data.State.OFF);
        }
        
        if (varName.equals("Gear") && d.getDouble() == 0.0)
            d.setValue("N");
        else
        //Turn the gauge off if the car is turned off
        if (!d.getState().equals(Data.State.NOTAVAILABLE) && m_IODriver.getVars().getDouble("Voltage") == 0.0) {
            d.setState(Data.State.OFF);
            d.setStatePercent(0.0);
        }
        
        return d;
    }
    
    protected Data _readVar() { return _readVar(m_varName); }
}
