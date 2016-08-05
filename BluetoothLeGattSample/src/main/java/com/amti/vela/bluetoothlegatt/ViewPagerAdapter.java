package com.amti.vela.bluetoothlegatt;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class ViewPagerAdapter extends FragmentStatePagerAdapter {

    ColorPickerFragment colorPickerFragment;
    DeviceFragment deviceFragment;
    DeveloperFragment devFragment;

    public ViewPagerAdapter(FragmentManager fm) {
        super(fm);
        colorPickerFragment = new ColorPickerFragment();
        deviceFragment = new DeviceFragment();
        devFragment = new DeveloperFragment();
    }

    @Override
    public Fragment getItem(int position) {
        switch (position)
        {
            case 0:
                return deviceFragment;
            case 1:
                return colorPickerFragment;
            case 2:
                return devFragment;
        }
        return new DeviceFragment();
        // Which Fragment should be displayed by the viewpager for the given position
        // In my case we are showing up only one fragment in all the three fragment_device so we are
        // not worrying about the position and just returning the DeviceFragment
    }

    public ColorPickerFragment getColorPickerFragment()
    {
        return colorPickerFragment;
    }

    public DeviceFragment getDeviceFragment()
    {
        return deviceFragment;
    }

    public DeveloperFragment getDevFragment()
    {
        return devFragment;
    }

    @Override
    public int getCount() {
        return 3;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position){
            case 0:
                return "Your Necklace";
            case 1:
                return "Choose a Color";
            case 2:
                return "Developer";
        }
        return "Default Text";
    }
}