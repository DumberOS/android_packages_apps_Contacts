package com.android.contacts.dpad;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.test.ActivityInstrumentationTestCase2;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.R;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.quickcontact.ExpandingEntryCardView.EntryView;
import java.util.ArrayList;
import java.util.List;

/** Uses a temporary local contact. Call/message listeners are stubbed before activation. */
public final class DpadTest extends ActivityInstrumentationTestCase2<QuickContactActivity> {
    private Uri rawContact;
    private QuickContactActivity activity;

    public DpadTest() { super(QuickContactActivity.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        ContentResolver resolver = getInstrumentation().getTargetContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.putNull(RawContacts.ACCOUNT_NAME);
        values.putNull(RawContacts.ACCOUNT_TYPE);
        values.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);
        rawContact = resolver.insert(RawContacts.CONTENT_URI, values);
        addData(StructuredName.CONTENT_ITEM_TYPE, StructuredName.DISPLAY_NAME,
                "D-pad navigation test contact");
        for (int i = 0; i < 8; i++) {
            addData(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER, "+1202555010" + i);
        }
        addData(Note.CONTENT_ITEM_TYPE, Note.NOTE, "Temporary navigation test note");
        Uri lookup = RawContacts.getContactLookupUri(resolver, rawContact);
        setActivityIntent(new Intent(ContactsContract.QuickContact.ACTION_QUICK_CONTACT, lookup));
        activity = getActivity();
        for (int i = 0; i < 100; i++) {
            settle();
            if (activity.hasWindowFocus() && !rows().isEmpty()) return;
        }
        fail("Contact did not load; unlock the phone before running tests");
    }

    private void addData(String mime, String column, String value) {
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, ContentUris.parseId(rawContact));
        values.put(Data.MIMETYPE, mime);
        values.put(column, value);
        getInstrumentation().getTargetContext().getContentResolver().insert(Data.CONTENT_URI, values);
    }

    @Override protected void tearDown() throws Exception {
        // ActivityInstrumentationTestCase2 clears test fields during super.tearDown().
        Uri contactToDelete = rawContact;
        try {
            super.tearDown();
        } finally {
            if (contactToDelete != null) {
                getInstrumentation().getTargetContext().getContentResolver().delete(
                        contactToDelete.buildUpon().appendQueryParameter(
                                ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(), null, null);
            }
        }
    }

    private void settle() {
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(250);
        getInstrumentation().waitForIdleSync();
    }

    private void key(int code) { getInstrumentation().sendKeyDownUpSync(code); settle(); }

    private void focus(View view) {
        getInstrumentation().setInTouchMode(false);
        getInstrumentation().runOnMainSync(() -> assertTrue(view.requestFocus()));
        settle();
    }

    private List<View> rows() {
        List<View> result = new ArrayList<>();
        getInstrumentation().runOnMainSync(() -> collectRows(
                activity.findViewById(R.id.communication_card), result));
        return result;
    }

    private void collectRows(View view, List<View> result) {
        if (view instanceof EntryView && view.isShown()) result.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectRows(group.getChildAt(i), result);
        }
    }

    private boolean inside(View ancestor, View child) {
        while (child != null) {
            if (child == ancestor) return true;
            child = child.getParent() instanceof View ? (View) child.getParent() : null;
        }
        return false;
    }

    private void tap(View view) {
        int[] xy = new int[2];
        getInstrumentation().runOnMainSync(() -> view.getLocationOnScreen(xy));
        long now = SystemClock.uptimeMillis();
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, now + 50, action,
                    xy[0] + view.getWidth() / 2f, xy[1] + view.getHeight() / 2f, 0);
            getInstrumentation().sendPointerSync(event);
            event.recycle();
        }
        settle();
    }

    public void testCompactHeaderAndToolbarNavigation() {
        assertEquals(0, activity.findViewById(R.id.transparent_view).getHeight());
        assertTrue(activity.findViewById(R.id.toolbar_parent).getHeight()
                <= activity.getResources().getDimensionPixelSize(R.dimen.quickcontact_compact_header_height));
        View first = rows().get(0);
        focus(first);
        key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue("Up must reach toolbar", inside(activity.findViewById(R.id.toolbar),
                activity.getCurrentFocus()));
        assertNotNull(activity.getCurrentFocus().getForeground());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue("Down must return to contact actions", inside(
                activity.findViewById(R.id.communication_card), activity.getCurrentFocus()));
        View edit = activity.findViewById(R.id.menu_edit);
        int[] clicks = new int[1];
        getInstrumentation().runOnMainSync(() -> edit.setOnClickListener(v -> clicks[0]++));
        focus(edit);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(1, clicks[0]);
        focus(first);
        tap(edit);
        assertEquals("First tap must activate toolbar action", 2, clicks[0]);
    }

    public void testRowActionsAndFirstTap() {
        View first = rows().get(0);
        View message = first.findViewById(R.id.icon_alternate);
        assertTrue(message.isShown());
        int[] clicks = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            first.setOnClickListener(v -> clicks[0]++);
            message.setOnClickListener(v -> clicks[1]++);
        });
        focus(first);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertSame(message, activity.getCurrentFocus());
        assertNotNull(message.getForeground());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(1, clicks[1]);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        assertSame(first, activity.getCurrentFocus());
        key(KeyEvent.KEYCODE_ENTER);
        assertEquals(1, clicks[0]);
        tap(message);
        assertEquals("First tap must activate message", 2, clicks[1]);
        tap(first);
        assertEquals("First tap must activate call row", 2, clicks[0]);
    }

    public void testScrollAndReturnToToolbar() {
        List<View> actions = rows();
        // Installed calling providers may add more actions for each phone number.
        assertTrue(actions.size() >= 8);
        focus(actions.get(0));
        for (int i = 1; i < actions.size(); i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        View focused = activity.getCurrentFocus();
        assertTrue("Must reach last action", inside(actions.get(actions.size() - 1), focused));
        Rect visible = new Rect();
        assertTrue(focused.getGlobalVisibleRect(visible));
        assertEquals("Focused row must be fully visible", focused.getHeight(), visible.height());
        for (int i = 0; i < actions.size(); i++) key(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue("Up must return to toolbar", inside(activity.findViewById(R.id.toolbar),
                activity.getCurrentFocus()));
    }

    public void testReadOnlyDetailsContextMenu() {
        List<View> details = new ArrayList<>();
        getInstrumentation().runOnMainSync(() -> collectRows(
                activity.findViewById(R.id.about_card), details));
        assertFalse(details.isEmpty());
        View note = details.get(0);
        focus(note);
        assertNotNull(note.getForeground());
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse("Center must open the detail's copy menu", activity.hasWindowFocus());
        key(KeyEvent.KEYCODE_BACK);
        assertTrue(activity.hasWindowFocus());
    }
}
