package app.studyflow;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.json.*;
import java.io.*;

/** Only saved attachment UUIDs can be shared; never the database or arbitrary private files. */
public final class AttachmentProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private JSONObject note(Uri uri)throws FileNotFoundException {
        String id=uri.getLastPathSegment();
        if(uri.getPathSegments().size()!=1||id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new FileNotFoundException("Invalid attachment");
        try{Store s=new Store(getContext());JSONArray notes=s.array("notes");for(int i=0;i<notes.length();i++){JSONObject n=notes.getJSONObject(i);if(n.optString("file").equals(id))return n;}}catch(Exception e){throw new FileNotFoundException("Attachment unavailable");}
        throw new FileNotFoundException("Attachment unavailable");
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");JSONObject n=note(uri);
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),n.optString("file")),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){try{return note(uri).optString("type","application/octet-stream");}catch(FileNotFoundException e){return "application/octet-stream";}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);
        try{JSONObject n=note(uri);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=n.optString("name");else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=new File(getContext().getFilesDir(),n.optString("file")).length();}cursor.addRow(values);}catch(FileNotFoundException ignored){}return cursor;
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException("Read only");}
}
