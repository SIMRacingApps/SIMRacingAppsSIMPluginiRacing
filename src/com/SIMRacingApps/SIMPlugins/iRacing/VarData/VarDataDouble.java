package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.SIMPlugins.iRacing.VarHeaders.VarHeader;

/**
 * This class overrides the Data class and reads the iRacingValue
 * 
 */
public class VarDataDouble extends Data {
    
    protected IODriver m_IODriver;
    protected int m_index = 0;
    protected VarHeader m_varHeader;
    protected iRacingCar m_car;
    private String m_origUOM;

    /**
     * Class constructor to use if the Var is not indexed.
     * 
     * @param IODriver The instance to read the data from
     * @param car The car this reader is attached to
     * @param name The name of the "Var" to read
     * @param defaultUOM The UOM to use if iRacing doesn't provide one  
     */
    public VarDataDouble(IODriver IODriver, iRacingCar car, String name,String defaultUOM) {
        super(name,0.0);
        m_IODriver = IODriver;
        m_car = car;
        m_varHeader = IODriver.getVarHeaders().getVarHeader(name);
        this.setUOM(defaultUOM);
        if (m_varHeader != null) {
            if (!m_varHeader.Unit.isEmpty())
                this.setUOM(m_varHeader.Unit);
        }
        m_origUOM = this.getUOM(name);
    }
    /**
     * Class constructor to use if the Var is indexed.
     * 
     * @param IODriver The instance to read the data from
     * @param car The car this reader is attached to
     * @param name The name of the "Var" to read
     * @param index The CarIdx value for the car you want to read
     * @param defaultUOM The UOM to use if iRacing doesn't provide one  
     */
    public VarDataDouble(IODriver IODriver, iRacingCar car, String name,int index,String defaultUOM) {
        super(name,0.0);
        m_IODriver = IODriver;
        m_car = car;
        m_index = index;
        m_varHeader = IODriver.getVarHeaders().getVarHeader(name);
        this.setUOM(defaultUOM);
        if (m_varHeader != null) {
            if (!m_varHeader.Unit.isEmpty())
                this.setUOM(m_varHeader.Unit);
        }
        m_origUOM = this.getUOM(name);
    }
    
    /**
     * Returns the most recent value from iRacing for this Var.
     * 
     * Also, sets the State if the Var requires it.
     * 
     * @param name The name of the Data
     * @return The value as a Double
     */
    @Override
    public Object getValue(String name) {
        double d = 0.0;

        if (m_varHeader == null)
            setState(Data.State.NOTAVAILABLE);
        else
        if (m_car.isValid()) {
            if (!name.isEmpty()){
                try {
                    d = m_IODriver.getVars().getDouble(name,m_index);
                } catch (IndexOutOfBoundsException e) {
                    setValue(name,d);
                    setState(Data.State.ERROR);
                    return d;
                }
//if (name.equals("FuelLevel")) d *= .25;
            }

            if (Double.isNaN(d)) {
                setState(Data.State.OFF);
            }
            else {
                if (m_origUOM.equals("%")
                &&  !name.equals("dcBrakeBias") //already in x100 percent.
                )
                    d *= 100.0;

                setState(Data.State.NORMAL);
            }
        }
        else {
            setState(Data.State.OFF);
        }

        setValue(name,d);


        if (name.substring(2).startsWith("coldPressure")){
            if (Double.isNaN(d) || d <= 0.0) {
                d=(new Data("ColdPressureDefault",32.0,"psi")).convertUOM(this.getUOM(name)).getDouble();
                setValue(d);
                setState(State.NORMAL);
            }
        }
        else
        if (name.substring(2).startsWith("temp")){
            if (Double.isNaN(d) || d <= 0.0) {
                d=(new Data("ColdTempDefault",72.0,"F")).convertUOM(this.getUOM(name)).getDouble();
                setValue(d);
                setState(State.NORMAL);
            }
        }
        else
        if (name.substring(2).startsWith("wear")){
            if (Double.isNaN(d) || d <= 0.0) {
                d=100.0;
                setValue(d);
                setState(State.NORMAL);
            }
        }

        return d;
    }
}
