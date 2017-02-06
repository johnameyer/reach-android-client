/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.reach.contacts.syncadapter;

import co.reach.contacts.client.NetworkUtilities;
import co.reach.contacts.client.RawContact;
import co.reach.contacts.platform.ContactManager;

import org.json.JSONException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * SyncAdapter implementation for syncing sample SyncAdapter contacts to the
 * platform ContactOperations provider.  This sample shows a basic 2-way
 * sync between the client and a sample server.  It also contains an
 * example of how to update the contacts' status messages, which
 * would be useful for a messaging or social networking client.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "SyncAdapter";
    private static final String SYNC_MARKER_KEY = "co.reach.contacts.marker";
    private static final boolean NOTIFY_AUTH_FAILURE = true;

    private final AccountManager mAccountManager;

    private final Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
        ContentProviderClient provider, SyncResult syncResult) {

        try {
            // see if we already have a sync-state attached to this account. By handing
            // This value to the server, we can just get the contacts that have
            // been updated on the server-side since our last sync-up
            long lastSyncMarker = getServerSyncMarker(account);

            // By default, contacts from a 3rd party provider are hidden in the contacts
            // list. So let's set the flag that causes them to be visible, so that users
            // can actually see these contacts.
            if (lastSyncMarker == 0) {
                ContactManager.setAccountContactsVisibility(getContext(), account, true);
            }

            List<RawContact> updatedContacts;

            // Make sure that the sample group exists
            final long groupId = ContactManager.ensureSampleGroupExists(mContext, account);

            // Retrieve the server-side changes
            updatedContacts = NetworkUtilities.syncContacts(account,
                    lastSyncMarker);


            // Update the local contacts database with the changes. updateContacts()
            // returns a syncState value that indicates the high-water-mark for
            // the changes we received.
            Log.w(TAG, "Calling contactManager's sync contacts");
            long newSyncState = ContactManager.updateContacts(mContext,
                    account.name==null?"account-name":account.name,
                    updatedContacts,
                    groupId,
                    lastSyncMarker);

            // Save off the new sync marker. On our next sync, we only want to receive
            // contacts that have changed since this sync...
            setServerSyncMarker(account, newSyncState);

        /**} catch (final AuthenticatorException e) {
            Log.e(TAG, "AuthenticatorException", e);
            syncResult.stats.numParseExceptions++;*/
        } catch (final IOException e) {
            Log.e(TAG, "IOException", e);
            syncResult.stats.numIoExceptions++;
        } catch (final JSONException e) {
            Log.e(TAG, "JSONException", e);
            syncResult.stats.numParseExceptions++;
        }
    }

    /**
     * This helper function fetches the last known high-water-mark
     * we received from the server - or 0 if we've never synced.
     * @param account the account we're syncing
     * @return the change high-water-mark
     */
    private long getServerSyncMarker(Account account) {
        String markerString = mAccountManager.getUserData(account, SYNC_MARKER_KEY);
        if (!TextUtils.isEmpty(markerString)) {
            return Long.parseLong(markerString);
        }
        return 0;
    }

    /**
     * Save off the high-water-mark we receive back from the server.
     * @param account The account we're syncing
     * @param marker The high-water-mark we want to save.
     */
    private void setServerSyncMarker(Account account, long marker) {
        mAccountManager.setUserData(account, SYNC_MARKER_KEY, Long.toString(marker));
    }
}

