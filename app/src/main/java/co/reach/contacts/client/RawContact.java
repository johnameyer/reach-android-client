package co.reach.contacts.client;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * @author jack1
 * @since 2/4/2017
 */

public class RawContact {
    public static final String TAG = "RawContact";

    private final String name;

    private final String phone;

    private final String email;

    private final String address;

    private final long serverContactId;

    private final long rawContactId;

    private final long syncState;

    private final boolean deleted;

    private final String avatarUrl;

    public RawContact(String name, String phone, String email, String avatarUrl, String address, boolean deleted, String serverContactId,
                      long rawContactId, long syncState) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.deleted = deleted;
        this.address = address;
        this.rawContactId = rawContactId;
        this.syncState = syncState;
        this.serverContactId = 0;
    }

    /**
     * Creates and returns an instance of the RawContact from the provided JSON data.
     *
     * @param contact The JSONObject containing user data
     * @return user The new instance of Sample RawContact created from the JSON data.
     */
    public static RawContact valueOf(JSONObject contact) {

        try {
            final String serverContactId = !contact.isNull("_id") ? contact.getString("_id") : null;
            // If we didn't get either a username or serverId for the contact, then
            // we can't do anything with it locally...
            if (serverContactId == null) {
                throw new JSONException("JSON contact missing required 'u' or 'i' fields");
            }

            final int rawContactId = !contact.isNull("c") ? contact.getInt("c") : 0;
            final String name = !contact.isNull("name") ? contact.getString("name") : null;
            final String phone = !contact.isNull("phone") ? contact.getString("phone") : null;
            final String email = !contact.isNull("email") ? contact.getString("email") : null;
            final String avatarUrl = !contact.isNull("avatar") ? contact.getString("avatar") : null;
            final boolean deleted = !contact.isNull("d") ? contact.getBoolean("d") : false;
            final long syncState = !contact.isNull("x") ? contact.getLong("x") : 0;
            final String address = null;//
            return new RawContact(name, phone,
                    email, avatarUrl,address, deleted,
                    serverContactId, rawContactId, syncState);
        } catch (final Exception ex) {
            Log.i(TAG, "Error parsing JSON contact object" + ex.toString());
        }
        return null;
    }

    /**
     * Creates and returns RawContact instance from all the supplied parameters.
     */
    public static RawContact create(String name, String phone, String email, String address, boolean deleted, long rawContactId,
                                    String serverContactId) {
        return new RawContact(name, phone, email, address, null, deleted, serverContactId, rawContactId,
                Integer.MAX_VALUE);
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public long getServerContactId() {
        return serverContactId;
    }

    public long getRawContactId() {
        return rawContactId;
    }

    public long getSyncState() {
        return syncState;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

}
