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
package co.reach.contacts.platform;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import co.reach.contacts.Constants;
import co.reach.contacts.R;
import co.reach.contacts.client.NetworkUtilities;

/**
 * Helper class for storing data in the platform content providers.
 */
public class ContactOperations {
    private final ContentValues mValues;
    private final BatchOperation mBatchOperation;
    private final Context mContext;
    private boolean mIsSyncOperation;
    private long mRawContactId;
    private int mBackReference;
    private boolean mIsNewContact;

    /**
     * Since we're sending a lot of contact provider operations in a single
     * batched operation, we want to make sure that we "yield" periodically
     * so that the RawContact Provider can write changes to the DB, and can
     * open a new transaction.  This prevents ANR (application not responding)
     * errors.  The recommended time to specify that a yield is permitted is
     * with the first operation on a particular contact.  So if we're updating
     * multiple fields for a single contact, we make sure that we call
     * withYieldAllowed(true) on the first field that we update. We use
     * mIsYieldAllowed to keep track of what value we should pass to
     * withYieldAllowed().
     */
    private boolean mIsYieldAllowed;

    public ContactOperations(Context context, boolean isSyncOperation,
                             BatchOperation batchOperation) {
        mValues = new ContentValues();
        mIsYieldAllowed = true;
        mIsSyncOperation = isSyncOperation;
        mContext = context;
        mBatchOperation = batchOperation;
    }

    public ContactOperations(Context context, long userId, String accountName,
                             boolean isSyncOperation, BatchOperation batchOperation) {
        this(context, isSyncOperation, batchOperation);
        mBackReference = mBatchOperation.size();
        mIsNewContact = true;
        mValues.put(RawContacts.SOURCE_ID, userId);
        mValues.put(RawContacts.ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
        mValues.put(RawContacts.ACCOUNT_NAME, accountName);
        ContentProviderOperation.Builder builder =
                newInsertCpo(RawContacts.CONTENT_URI, mIsSyncOperation, true).withValues(mValues);
        mBatchOperation.add(builder.build());
    }

    public ContactOperations(Context context, long rawContactId, boolean isSyncOperation,
                             BatchOperation batchOperation) {
        this(context, isSyncOperation, batchOperation);
        mIsNewContact = false;
        mRawContactId = rawContactId;
    }

    /**
     * Returns an instance of ContactOperations instance for adding new contact
     * to the platform contacts provider.
     *
     * @param context         the Authenticator Activity context
     * @param userId          the userId of the sample SyncAdapter user object
     * @param accountName     the username for the SyncAdapter account
     * @param isSyncOperation are we executing this as part of a sync operation?
     * @return instance of ContactOperations
     */
    public static ContactOperations createNewContact(Context context, long userId,
                                                     String accountName, boolean isSyncOperation, BatchOperation batchOperation) {
        return new ContactOperations(context, userId, accountName, isSyncOperation, batchOperation);
    }

    /**
     * Returns an instance of ContactOperations for updating existing contact in
     * the platform contacts provider.
     *
     * @param context         the Authenticator Activity context
     * @param rawContactId    the unique Id of the existing rawContact
     * @param isSyncOperation are we executing this as part of a sync operation?
     * @return instance of ContactOperations
     */
    public static ContactOperations updateExistingContact(Context context, long rawContactId,
                                                          boolean isSyncOperation, BatchOperation batchOperation) {
        return new ContactOperations(context, rawContactId, isSyncOperation, batchOperation);
    }

    public static ContentProviderOperation.Builder newInsertCpo(Uri uri,
                                                                boolean isSyncOperation, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newInsert(addCallerIsSyncAdapterParameter(uri, isSyncOperation))
                .withYieldAllowed(isYieldAllowed);
    }

    public static ContentProviderOperation.Builder newUpdateCpo(Uri uri,
                                                                boolean isSyncOperation, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newUpdate(addCallerIsSyncAdapterParameter(uri, isSyncOperation))
                .withYieldAllowed(isYieldAllowed);
    }

    public static ContentProviderOperation.Builder newDeleteCpo(Uri uri,
                                                                boolean isSyncOperation, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newDelete(addCallerIsSyncAdapterParameter(uri, isSyncOperation))
                .withYieldAllowed(isYieldAllowed);
    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri, boolean isSyncOperation) {
        if (isSyncOperation) {
            // If we're in the middle of a real sync-adapter operation, then go ahead
            // and tell the Contacts provider that we're the sync adapter.  That
            // gives us some special permissions - like the ability to really
            // delete a contact, and the ability to clear the dirty flag.
            //
            // If we're not in the middle of a sync operation (for example, we just
            // locally created/edited a new contact), then we don't want to use
            // the special permissions, and the system will automagically mark
            // the contact as 'dirty' for us!
            return uri.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                    .build();
        }
        return uri;
    }

    public ContactOperations addEmail(String email) {
        mValues.clear();
        if (!TextUtils.isEmpty(email)) {
            mValues.put(Email.DATA, email);
            mValues.put(Email.TYPE, Email.TYPE_OTHER);
            mValues.put(Email.MIMETYPE, Email.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }

    public ContactOperations addAddress(String address) {
        mValues.clear();
        if (!TextUtils.isEmpty(address)) {
            mValues.put(StructuredPostal.DATA, address);
            mValues.put(StructuredPostal.TYPE, StructuredPostal.TYPE_OTHER);
            mValues.put(StructuredPostal.MIMETYPE, Email.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }

    /**
     * Adds a phone number
     *
     * @param phone     new phone number for the contact
     * @return instance of ContactOperations
     */
    public ContactOperations addPhone(String phone) {
        mValues.clear();
        if (!TextUtils.isEmpty(phone)) {
            mValues.put(Phone.NUMBER, phone);
            mValues.put(Phone.TYPE, Phone.TYPE_WORK);
            mValues.put(Phone.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }

    public ContactOperations addAvatar(String avatarUrl) {
        if (avatarUrl != null) {
            byte[] avatarBuffer = NetworkUtilities.downloadAvatar(avatarUrl);
            if (avatarBuffer != null) {
                mValues.clear();
                mValues.put(Photo.PHOTO, avatarBuffer);
                mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
                addInsertOp();
            }
        }
        return this;
    }

    public ContactOperations addName(String fullName) {
        mValues.clear();

        if (!TextUtils.isEmpty(fullName)) {
            mValues.put(StructuredName.DISPLAY_NAME, fullName);
            mValues.put(StructuredName.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }


    /**
     * Adds a profile action
     *
     * @param userId the userId of the sample SyncAdapter user object
     * @return instance of ContactOperations
     */
    public ContactOperations addProfileAction(long userId) {
        mValues.clear();
        if (userId != 0) {
            mValues.put(SampleSyncAdapterColumns.DATA_PID, userId);
            mValues.put(SampleSyncAdapterColumns.DATA_SUMMARY, mContext
                    .getString(R.string.profile_action));
            mValues.put(SampleSyncAdapterColumns.DATA_DETAIL, mContext
                    .getString(R.string.view_profile));
            mValues.put(Data.MIMETYPE, SampleSyncAdapterColumns.MIME_PROFILE);
            addInsertOp();
        }
        return this;
    }

    /**
     * Updates contact's serverId
     *
     * @param serverId the serverId for this contact
     * @param uri      Uri for the existing raw contact to be updated
     * @return instance of ContactOperations
     */
    public ContactOperations updateServerId(long serverId, Uri uri) {
        mValues.clear();
        mValues.put(RawContacts.SOURCE_ID, serverId);
        addUpdateOp(uri);
        return this;
    }

    /**
     * Updates contact's email
     *
     * @param email email id of the sample SyncAdapter user
     * @param uri   Uri for the existing raw contact to be updated
     * @return instance of ContactOperations
     */
    public ContactOperations updateEmail(String email, String existingEmail, Uri uri) {
        if (!TextUtils.equals(existingEmail, email)) {
            mValues.clear();
            mValues.put(Email.DATA, email);
            addUpdateOp(uri);
        }
        return this;
    }

    public ContactOperations updateName(Uri uri,
                                        String existingName,
                                        String name) {

        mValues.clear();
        if (!TextUtils.equals(existingName, name)) {
            mValues.put(StructuredName.DISPLAY_NAME, name);
        }
        if (mValues.size() > 0) {
            addUpdateOp(uri);
        }
        return this;
    }

    /**
     * Updates contact's phone
     *
     * @param existingNumber phone number stored in contacts provider
     * @param phone          new phone number for the contact
     * @param uri            Uri for the existing raw contact to be updated
     * @return instance of ContactOperations
     */
    public ContactOperations updatePhone(String existingNumber, String phone, Uri uri) {
        if (!TextUtils.equals(phone, existingNumber)) {
            mValues.clear();
            mValues.put(Phone.NUMBER, phone);
            addUpdateOp(uri);
        }
        return this;
    }

    public ContactOperations updateAddress(String existingAddress, String address, Uri uri) {
        if (!TextUtils.equals(address, existingAddress)) {
            mValues.clear();
            mValues.put(StructuredPostal.FORMATTED_ADDRESS, address);
            addUpdateOp(uri);
        }
        return this;
    }

    public ContactOperations updateAvatar(String avatarUrl, Uri uri) {
        if (avatarUrl != null) {
            byte[] avatarBuffer = NetworkUtilities.downloadAvatar(avatarUrl);
            if (avatarBuffer != null) {
                mValues.clear();
                mValues.put(Photo.PHOTO, avatarBuffer);
                mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
                addUpdateOp(uri);
            }
        }
        return this;
    }

    /**
     * Updates contact's profile action
     *
     * @param userId sample SyncAdapter user id
     * @param uri    Uri for the existing raw contact to be updated
     * @return instance of ContactOperations
     */
    public ContactOperations updateProfileAction(Integer userId, Uri uri) {
        mValues.clear();
        mValues.put(SampleSyncAdapterColumns.DATA_PID, userId);
        addUpdateOp(uri);
        return this;
    }

    /**
     * Adds an insert operation into the batch
     */
    private void addInsertOp() {

        if (!mIsNewContact) {
            mValues.put(Phone.RAW_CONTACT_ID, mRawContactId);
        }
        ContentProviderOperation.Builder builder =
                newInsertCpo(Data.CONTENT_URI, mIsSyncOperation, mIsYieldAllowed);
        builder.withValues(mValues);
        if (mIsNewContact) {
            builder.withValueBackReference(Data.RAW_CONTACT_ID, mBackReference);
        }
        mIsYieldAllowed = false;
        mBatchOperation.add(builder.build());
    }

    /**
     * Adds an update operation into the batch
     */
    private void addUpdateOp(Uri uri) {
        ContentProviderOperation.Builder builder =
                newUpdateCpo(uri, mIsSyncOperation, mIsYieldAllowed).withValues(mValues);
        mIsYieldAllowed = false;
        mBatchOperation.add(builder.build());
    }
}
