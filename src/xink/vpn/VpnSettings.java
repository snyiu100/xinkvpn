package xink.vpn;

import static xink.vpn.Constants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xink.vpn.editor.EditAction;
import xink.vpn.editor.VpnProfileEditor;
import xink.vpn.wrapper.VpnProfile;
import xink.vpn.wrapper.VpnType;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;

public class VpnSettings extends Activity {

    private static final String ROWITEM_KEY = "vpn";
    private static final String TAG = "xink";
    private static final String[] VPN_VIEW_KEYS = new String[] { ROWITEM_KEY };
    private static final int[] VPN_VIEWS = new int[] { R.id.radioActive };

    private VpnProfileRepository repository;
    private ListView vpnListView;
    private List<Map<String, VpnViewItem>> vpnListViewContent;
    private VpnViewBinder vpnViewBinder = new VpnViewBinder();
    private VpnViewItem activeVpnItem;
    private SimpleAdapter vpnListAdapter;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.selectVpn);
        setContentView(R.layout.vpn_list);

        ((TextView) findViewById(R.id.btnAddVpn)).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(final View v) {
                onAddVpn();
            }
        });

        vpnListViewContent = new ArrayList<Map<String, VpnViewItem>>();
        repository = VpnProfileRepository.getInstance(getApplicationContext());
        vpnListView = (ListView) findViewById(R.id.listVpns);

        refreshVpnList();
    }

    private void refreshVpnList() {
        loadContent();

        vpnListAdapter = new SimpleAdapter(this, vpnListViewContent, R.layout.vpn_profile, VPN_VIEW_KEYS, VPN_VIEWS);
        vpnListAdapter.setViewBinder(vpnViewBinder);
        vpnListView.setAdapter(vpnListAdapter);
        registerForContextMenu(vpnListView);
    }

    private void loadContent() {
        vpnListViewContent.clear();
        activeVpnItem = null;

        String activeProfileId = repository.getActiveProfileId();
        List<VpnProfile> allVpnProfiles = repository.getAllVpnProfiles();

        for (VpnProfile vpnProfile : allVpnProfiles) {
            addToProfileListView(activeProfileId, vpnProfile);
        }
    }

    private void addToProfileListView(final String activeProfileId, final VpnProfile vpnProfile) {
        if (vpnProfile == null) {
            return;
        }

        VpnViewItem item = makeVpnViewItem(activeProfileId, vpnProfile);

        Map<String, VpnViewItem> row = new HashMap<String, VpnViewItem>();
        row.put(ROWITEM_KEY, item);

        vpnListViewContent.add(row);
    }

    private VpnViewItem makeVpnViewItem(final String activeProfileId, final VpnProfile vpnProfile) {
        VpnViewItem item = new VpnViewItem();
        item.profile = vpnProfile;

        if (vpnProfile.getId().equals(activeProfileId)) {
            item.isActive = true;
            activeVpnItem = item;
        }
        return item;
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        VpnViewItem selectedVpnItem = getVpnViewItemAt(info.position);
        menu.setHeaderTitle(selectedVpnItem.profile.getName());

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.vpn_list_context_menu, menu);
    }

    @SuppressWarnings("unchecked")
    private VpnViewItem getVpnViewItemAt(final int pos) {
        return ((Map<String, VpnViewItem>) vpnListAdapter.getItem(pos)).get(ROWITEM_KEY);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        boolean consumed = false;
        int itemId = item.getItemId();
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        VpnViewItem vpnItem = getVpnViewItemAt(info.position);

        switch (itemId) {
        case R.id.menu_del_vpn:
            onDeleteVpn(vpnItem);
            consumed = true;
            break;
        case R.id.menu_edit_vpn:
            onEditVpn(vpnItem);
            consumed = true;
            break;
        default:
            consumed = super.onContextItemSelected(item);
            break;
        }

        return consumed;
    }

    private void onAddVpn() {
        startActivityForResult(new Intent(this, VpnTypeSelection.class), REQ_SELECT_VPN_TYPE);
    }

    private void onDeleteVpn(final VpnViewItem vpnItem) {
        repository.deleteVpnProfile(vpnItem.profile);
        refreshVpnList();
    }

    private void onEditVpn(final VpnViewItem vpnItem) {
        Log.d(TAG, "onEditVpn");

        VpnProfile p = vpnItem.profile;
        editVpn(p);
    }

    private void editVpn(final VpnProfile p) {
        VpnType type = p.getType();

        Class<? extends VpnProfileEditor> editorClass = type.getEditorClass();
        if (editorClass == null) {
            Log.d(TAG, "editor class is null for " + type);
            return;
        }

        Intent intent = new Intent(this, editorClass);
        intent.setAction(EditAction.EDIT.toString());
        intent.putExtra(KEY_VPN_PROFILE_NAME, p.getName());
        startActivityForResult(intent, REQ_EDIT_VPN);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.vpn_list_menu, menu);

        return true;
    }

    /**
     * Handles item selections
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        boolean consumed = false;
        int itemId = item.getItemId();

        switch (itemId) {
        default:
            consumed = super.onContextItemSelected(item);
            break;
        }

        return consumed;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (data == null) {
            return;
        }

        switch (requestCode) {
        case REQ_SELECT_VPN_TYPE:
            onVpnTypePicked(data);
            break;
        case REQ_ADD_VPN:
            onVpnProfileAdded(data);
            break;
        case REQ_EDIT_VPN:
            onVpnProfileEdited(data);
            break;
        default:
            Log.w(TAG, "onActivityResult, unknown reqeustCode " + requestCode + ", result=" + resultCode + ", data=" + data);
            break;
        }
    }

    private void onVpnTypePicked(final Intent data) {
        VpnType pickedVpnType = (VpnType) data.getExtras().get(KEY_VPN_TYPE);
        addVpn(pickedVpnType);
    }

    private void addVpn(final VpnType vpnType) {
        Log.i(TAG, "add vpn " + vpnType);
        Class<? extends VpnProfileEditor> editorClass = vpnType.getEditorClass();

        if (editorClass == null) {
            Log.d(TAG, "editor class is null for " + vpnType);
            return;
        }

        Intent intent = new Intent(this, editorClass);
        intent.setAction(EditAction.CREATE.toString());
        startActivityForResult(intent, REQ_ADD_VPN);
    }

    private void onVpnProfileAdded(final Intent data) {
        Log.i(TAG, "new vpn profile created");

        String name = data.getStringExtra(KEY_VPN_PROFILE_NAME);
        VpnProfile profile = repository.getProfileByName(name);

        addToProfileListView(repository.getActiveProfileId(), profile);
        updateProfileListView();
    }

    private void onVpnProfileEdited(final Intent data) {
        Log.i(TAG, "vpn profile modified");
        updateProfileListView();
    }

    @Override
    protected void onStop() {
        save();
        super.onStop();
    }

    private void save() {
        repository.save();
    }

    private void vpnItemActivated(final VpnViewItem activatedItem) {
        if (activeVpnItem == activatedItem) {
            return;
        }

        if (activeVpnItem != null) {
            activeVpnItem.isActive = false;
        }

        activeVpnItem = activatedItem;
        repository.setActiveProfile(activeVpnItem.profile);
        updateProfileListView();
    }

    private void updateProfileListView() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                vpnListAdapter.notifyDataSetChanged();
            }
        });
    }

    static final class VpnViewBinder implements ViewBinder {

        @Override
        public boolean setViewValue(final View view, final Object data, final String textRepresentation) {
            if (!(data instanceof VpnViewItem)) {
                return false;
            }

            VpnViewItem item = (VpnViewItem) data;
            boolean bound = false;

            if (view instanceof RadioButton) {
                bindVpnItem((RadioButton) view, item);
                bound = true;
            }

            return bound;
        }

        private void bindVpnItem(final RadioButton view, final VpnViewItem item) {
            view.setText(item.profile.getName());

            view.setOnCheckedChangeListener(null);
            view.setOnLongClickListener(null);

            view.setChecked(item.isActive);
            view.setOnCheckedChangeListener(item);
        }
    }

    final class VpnViewItem implements OnCheckedChangeListener {
        VpnProfile profile;
        boolean isActive;

        @Override
        public void onCheckedChanged(final CompoundButton button, final boolean isChecked) {
            if (isActive == isChecked) {
                return;
            }

            isActive = isChecked;

            if (isActive) {
                vpnItemActivated(this);
            }
        }

        @Override
        public String toString() {
            return profile.getName();
        }
    }
}
