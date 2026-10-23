package com.jruiz.nexo.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mantenimiento local: cada visita al taller es una fila y la lista de tareas
 * es una fotografía de lo realizado (no se altera al renombrar el catálogo).
 * Sin permisos nuevos, red, sincronización ni modificación de items existentes.
 */
public final class VehicleRepository {
    private VehicleRepository() {}

    static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS vehicles (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT NOT NULL," +
            "plate TEXT NOT NULL DEFAULT ''," +
            "mileage INTEGER NOT NULL DEFAULT 0," +
            "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS vehicle_services (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "vehicle_id INTEGER NOT NULL," +
            "service_date TEXT NOT NULL," +
            "mileage INTEGER," +
            "cost_cents INTEGER," +
            "operations_json TEXT NOT NULL," +
            "attachment_path TEXT NOT NULL DEFAULT ''," +
            "attachment_name TEXT NOT NULL DEFAULT ''," +
            "attachment_mime TEXT NOT NULL DEFAULT ''," +
            "created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vehicle_services_vehicle_date ON vehicle_services(vehicle_id,service_date DESC,id DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS vehicle_custom_operations (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "label TEXT NOT NULL COLLATE NOCASE UNIQUE," +
            "active INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE IF NOT EXISTS vehicle_due_rules (" +
            "vehicle_id INTEGER NOT NULL," +
            "operation TEXT NOT NULL," +
            "due_date TEXT NOT NULL DEFAULT ''," +
            "due_mileage INTEGER," +
            "PRIMARY KEY(vehicle_id,operation))");
    }

    private static JSONObject error(String message) {
        JSONObject j = new JSONObject();
        try { j.put("ok", false).put("error", message); } catch (Exception ignored) {}
        return j;
    }
    private static JSONObject success(long id) {
        JSONObject j = new JSONObject();
        try { j.put("ok", true).put("id", id); } catch (Exception ignored) {}
        return j;
    }
    private static boolean validDate(String date) {
        try { return date != null && date.matches("\\d{4}-\\d{2}-\\d{2}") &&
            LocalDate.parse(date).toString().equals(date); }
        catch (Exception ignored) { return false; }
    }
    private static boolean validMileage(long km) { return km >= 0 && km <= 10000000; }
    private static boolean exists(SQLiteDatabase sql, long id) {
        if (id <= 0) return false;
        try (Cursor c = sql.rawQuery("SELECT 1 FROM vehicles WHERE id=?",
                new String[]{Long.toString(id)})) { return c.moveToFirst(); }
    }
    private static JSONObject vehicleRow(Cursor c) {
        JSONObject j = new JSONObject();
        try {
            j.put("id",c.getLong(0)); j.put("name",c.getString(1));
            j.put("plate",c.getString(2)); j.put("mileage",c.getLong(3));
            j.put("serviceCount",c.getLong(4));
        } catch (Exception ignored) {}
        return j;
    }

    public static JSONArray vehicles(DatabaseHelper helper) {
        JSONArray result = new JSONArray();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
            "SELECT v.id,v.name,v.plate,v.mileage,COUNT(s.id) FROM vehicles v " +
            "LEFT JOIN vehicle_services s ON s.vehicle_id=v.id " +
            "GROUP BY v.id ORDER BY v.created_at,v.id", null)) {
            while(c.moveToNext()) result.put(vehicleRow(c));
        }
        return result;
    }
    public static JSONObject saveVehicle(DatabaseHelper helper, JSONObject input) {
        String name=input.optString("name","").trim();
        String plate=input.optString("plate","").trim().toUpperCase(java.util.Locale.ROOT);
        long km=input.optLong("mileage",-1L),id=input.optLong("id",0L);
        if(name.isEmpty()||name.length()>80||plate.length()>24||!validMileage(km))
            return error("Revisa el nombre y el kilometraje del vehículo.");
        SQLiteDatabase sql=helper.getWritableDatabase();
        if(id>0&&!exists(sql,id))return error("El vehículo ya no existe.");
        ContentValues v=new ContentValues();
        v.put("name",name);v.put("plate",plate);v.put("mileage",km);
        v.put("updated_at",System.currentTimeMillis());
        if(id>0)sql.update("vehicles",v,"id=?",new String[]{Long.toString(id)});
        else {v.put("created_at",System.currentTimeMillis());id=sql.insert("vehicles",null,v);}
        return id>0?success(id):error("No se pudo guardar el vehículo.");
    }
    public static boolean removeVehicle(DatabaseHelper helper,long id) {
        SQLiteDatabase sql=helper.getWritableDatabase();
        if(!exists(sql,id))return false;
        sql.beginTransaction();
        try {
            String[] args={Long.toString(id)};
            sql.delete("vehicle_services","vehicle_id=?",args);
            sql.delete("vehicle_due_rules","vehicle_id=?",args);
            int deleted=sql.delete("vehicles","id=?",args);
            sql.setTransactionSuccessful();
            return deleted>0;
        } finally {sql.endTransaction();}
    }

    public static JSONArray services(DatabaseHelper helper,long vehicleId) {
        JSONArray result=new JSONArray();
        if(vehicleId<=0)return result;
        try(Cursor c=helper.getReadableDatabase().rawQuery(
            "SELECT id,vehicle_id,service_date,mileage,cost_cents,operations_json," +
            "attachment_path,attachment_name,attachment_mime FROM vehicle_services " +
            "WHERE vehicle_id=? ORDER BY service_date DESC,id DESC",
            new String[]{Long.toString(vehicleId)})) {
            while(c.moveToNext()) {
                JSONObject j=new JSONObject();
                try {
                    j.put("id",c.getLong(0));j.put("vehicleId",c.getLong(1));
                    j.put("date",c.getString(2));
                    j.put("mileage",c.isNull(3)?JSONObject.NULL:c.getLong(3));
                    j.put("costCents",c.isNull(4)?JSONObject.NULL:c.getLong(4));
                    j.put("operations",new JSONArray(c.getString(5)));
                    j.put("attachmentPath",c.getString(6));
                    j.put("attachmentName",c.getString(7));
                    j.put("attachmentMime",c.getString(8));
                }catch(Exception ignored){}
                result.put(j);
            }
        }
        return result;
    }
    public static JSONObject saveService(DatabaseHelper helper,JSONObject input) {
        long vehicleId=input.optLong("vehicleId",0L),id=input.optLong("id",0L);
        SQLiteDatabase sql=helper.getWritableDatabase();
        if(!exists(sql,vehicleId))return error("Selecciona un vehículo existente.");
        String date=input.optString("date","");
        if(!validDate(date))return error("Fecha de mantenimiento no válida.");
        JSONArray raw=input.optJSONArray("operations");
        if(raw==null||raw.length()<1||raw.length()>30)return error("Selecciona entre 1 y 30 operaciones.");
        Set<String> operations=new LinkedHashSet<>();
        for(int n=0;n<raw.length();n++){
            String item=raw.optString(n,"").trim();
            if(item.isEmpty()||item.length()>90)return error("Operación de mantenimiento no válida.");
            operations.add(item);
        }
        if(operations.isEmpty())return error("Selecciona al menos una operación.");
        Long mileage=null,cost=null;
        if(input.has("mileage")&&!input.isNull("mileage")){
            mileage=input.optLong("mileage",-1L);
            if(!validMileage(mileage))return error("Kilometraje no válido.");
        }
        if(input.has("costCents")&&!input.isNull("costCents")){
            cost=input.optLong("costCents",-1L);
            if(cost<0||cost>100000000L)return error("Coste no válido.");
        }
        if(id>0){
            try(Cursor c=sql.rawQuery("SELECT vehicle_id FROM vehicle_services WHERE id=?",
                    new String[]{Long.toString(id)})){
                if(!c.moveToFirst()||c.getLong(0)!=vehicleId)
                    return error("La actuación ya no existe en este vehículo.");
            }
        }
        ContentValues v=new ContentValues();
        v.put("vehicle_id",vehicleId);v.put("service_date",date);
        if(mileage==null)v.putNull("mileage");else v.put("mileage",mileage);
        if(cost==null)v.putNull("cost_cents");else v.put("cost_cents",cost);
        v.put("operations_json",new JSONArray(operations).toString());
        v.put("attachment_path",input.optString("attachmentPath",""));
        v.put("attachment_name",input.optString("attachmentName",""));
        v.put("attachment_mime",input.optString("attachmentMime",""));
        v.put("updated_at",System.currentTimeMillis());
        sql.beginTransaction();
        try {
            if(id>0)sql.update("vehicle_services",v,"id=?",new String[]{Long.toString(id)});
            else {v.put("created_at",System.currentTimeMillis());id=sql.insert("vehicle_services",null,v);}
            if(id<=0)return error("No se pudo guardar la actuación.");
            if(mileage!=null) {
                ContentValues km=new ContentValues();km.put("mileage",mileage);
                km.put("updated_at",System.currentTimeMillis());
                sql.update("vehicles",km,"id=? AND mileage<?",
                    new String[]{Long.toString(vehicleId),Long.toString(mileage)});
            }
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
        return success(id);
    }
    public static boolean removeService(DatabaseHelper helper,long id) {
        return id>0&&helper.getWritableDatabase().delete("vehicle_services","id=?",
            new String[]{Long.toString(id)})>0;
    }

    public static JSONArray customOperations(DatabaseHelper helper) {
        JSONArray out=new JSONArray();
        try(Cursor c=helper.getReadableDatabase().rawQuery(
            "SELECT id,label FROM vehicle_custom_operations WHERE active=1 ORDER BY label COLLATE NOCASE",null)) {
            while(c.moveToNext()){
                JSONObject j=new JSONObject();
                try{j.put("id",c.getLong(0));j.put("label",c.getString(1));}catch(Exception ignored){}
                out.put(j);
            }
        }
        return out;
    }
    public static JSONObject saveCustomOperation(DatabaseHelper helper,JSONObject input) {
        String label=input.optString("label","").replaceAll("\\s+"," ").trim();
        long id=input.optLong("id",0L);
        if(label.isEmpty()||label.length()>90)return error("El nombre debe tener entre 1 y 90 caracteres.");
        SQLiteDatabase sql=helper.getWritableDatabase();
        try {
            ContentValues v=new ContentValues();v.put("label",label);v.put("active",1);
            if(id>0) {
                if(sql.update("vehicle_custom_operations",v,"id=?",
                    new String[]{Long.toString(id)})!=1)return error("Operación no encontrada.");
            } else {
                try(Cursor c=sql.rawQuery(
                    "SELECT id FROM vehicle_custom_operations WHERE label=? COLLATE NOCASE",
                    new String[]{label})) {
                    if(c.moveToFirst()){
                        id=c.getLong(0);
                        sql.update("vehicle_custom_operations",v,"id=?",
                            new String[]{Long.toString(id)});
                    }else id=sql.insertOrThrow("vehicle_custom_operations",null,v);
                }
            }
            JSONObject result=success(id);result.put("label",label);return result;
        }catch(Exception ignored){return error("Ya existe una operación con ese nombre.");}
    }
    public static boolean hideCustomOperation(DatabaseHelper helper,long id) {
        if(id<=0)return false;
        ContentValues v=new ContentValues();v.put("active",0);
        return helper.getWritableDatabase().update("vehicle_custom_operations",v,
            "id=?",new String[]{Long.toString(id)})>0;
    }

    public static JSONArray dueRules(DatabaseHelper helper,long vehicleId){
        JSONArray result=new JSONArray();
        if(vehicleId<=0)return result;
        try(Cursor c=helper.getReadableDatabase().rawQuery(
            "SELECT operation,due_date,due_mileage FROM vehicle_due_rules WHERE vehicle_id=? ORDER BY operation",
            new String[]{Long.toString(vehicleId)})){
            while(c.moveToNext()){
                JSONObject j=new JSONObject();
                try{
                    j.put("operation",c.getString(0));j.put("dueDate",c.getString(1));
                    j.put("dueMileage",c.isNull(2)?JSONObject.NULL:c.getLong(2));
                }catch(Exception ignored){}
                result.put(j);
            }
        }
        return result;
    }
    public static JSONObject saveDueRule(DatabaseHelper helper,JSONObject input){
        long id=input.optLong("vehicleId",0L);
        String operation=input.optString("operation","").trim();
        String date=input.optString("dueDate","").trim();
        Long mileage=null;
        if(input.has("dueMileage")&&!input.isNull("dueMileage")){
            mileage=input.optLong("dueMileage",-1L);
            if(!validMileage(mileage))return error("Kilometraje previsto no válido.");
        }
        if(!exists(helper.getWritableDatabase(),id))return error("Vehículo no encontrado.");
        if(operation.isEmpty()||operation.length()>90||
            (!date.isEmpty()&&!validDate(date))||(date.isEmpty()&&mileage==null))
            return error("Indica una operación y una fecha o kilometraje.");
        ContentValues v=new ContentValues();
        v.put("vehicle_id",id);v.put("operation",operation);v.put("due_date",date);
        if(mileage==null)v.putNull("due_mileage");else v.put("due_mileage",mileage);
        helper.getWritableDatabase().insertWithOnConflict("vehicle_due_rules",null,v,
            SQLiteDatabase.CONFLICT_REPLACE);
        return success(id);
    }
    public static boolean removeDueRule(DatabaseHelper helper,long vehicleId,String operation){
        return vehicleId>0&&!operation.isBlank()&&
            helper.getWritableDatabase().delete("vehicle_due_rules",
                "vehicle_id=? AND operation=?",new String[]{Long.toString(vehicleId),operation})>0;
    }
}
