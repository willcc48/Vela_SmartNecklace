package com.amti.vela.bluetoothlegatt;

import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class CustomListAdapter extends ArrayAdapter<BluetoothDevice> {

    public CustomListAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    public CustomListAdapter(Context context, int resource, ArrayList<BluetoothDevice> devices) {
        super(context, resource, devices);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View view = convertView;

        if (view == null) {
            LayoutInflater vi;
            vi = LayoutInflater.from(getContext());
            view = vi.inflate(R.layout.custom_listview_item, null);
        }

        BluetoothDevice btDevice = getItem(position);

        if (btDevice != null) {
            TextView text = (TextView)view.findViewById(R.id.list_item_text);
            TextView subtext = (TextView)view.findViewById(R.id.list_item_subtext);

            //split text and subtext by the newline character
            String deviceName = btDevice.getName();
            String deviceAddress = btDevice.getAddress();

            if (text != null) {
                if(deviceName == null)
                    deviceName = "Unknown Device";
                text.setText(deviceName);
            }

            if (subtext != null) {
                subtext.setText(deviceAddress);
            }
        }

        return view;
    }




}
