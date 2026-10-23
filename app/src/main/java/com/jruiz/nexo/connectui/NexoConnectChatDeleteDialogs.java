package com.jruiz.nexo.connectui;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;
import com.jruiz.nexo.connect.vault.NexoConnectChatDeletion;
import com.jruiz.nexo.connect.vault.NexoConnectMigration;
import java.util.Collection;
import java.util.UUID;

/** Confirm destructive changes locally. Never modifies device pairing or remote data. */
final class NexoConnectChatDeleteDialogs {
    private static final int RED=0xffbc2840;
    private NexoConnectChatDeleteDialogs(){}

    static void showConversationMenu(Activity activity,String contactName,Runnable delete){
        new AlertDialog.Builder(activity).setTitle(contactName)
            .setItems(new String[]{"Eliminar conversación"},(dialog,which)->{
                if(which==0)delete.run();
            }).show();
    }

    static void confirmConversation(Activity activity,UUID owner,UUID peer,
            String name,Runnable completed){
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("¿Eliminar esta conversación?")
            .setMessage("Se borrará todo el historial de mensajes con "+name+
                    " en este teléfono. Esta acción no se puede deshacer. El contacto seguirá vinculado.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Eliminar",(d,which)->{
                try(SQLiteDatabase db=NexoConnectMigration.inbox(activity)){
                    NexoConnectChatDeletion.deleteConversation(db,owner,peer,System.currentTimeMillis());
                    Toast.makeText(activity,"Conversación eliminada",Toast.LENGTH_SHORT).show();
                    completed.run();
                }catch(Exception ex){
                    new AlertDialog.Builder(activity).setTitle("No se pudo eliminar")
                        .setMessage("El historial sigue disponible. Vuelve a intentarlo.")
                        .setPositiveButton("Entendido",null).show();
                }
            }).create();
        dialog.setOnShowListener(v->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(RED));
        dialog.show();
    }

    static void confirmMessages(Activity activity,UUID owner,UUID peer,
            Collection<String> deliveries,Runnable completed){
        if(deliveries==null||deliveries.isEmpty())return;
        int count=deliveries.size();
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle(count==1?"¿Eliminar mensaje?":"¿Eliminar mensajes?")
            .setMessage("Se eliminarán "+(count==1?"este mensaje":"los "+count+" mensajes seleccionados")+
                    " solo de este teléfono.")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Eliminar para mí",(d,which)->{
                try(SQLiteDatabase db=NexoConnectMigration.inbox(activity)){
                    NexoConnectChatDeletion.deleteMessages(db,owner,peer,deliveries);
                    Toast.makeText(activity,count==1?"Mensaje eliminado":"Mensajes eliminados",
                            Toast.LENGTH_SHORT).show();
                    completed.run();
                }catch(Exception ex){
                    new AlertDialog.Builder(activity).setTitle("No se pudo eliminar")
                        .setMessage("Los mensajes siguen disponibles. Vuelve a intentarlo.")
                        .setPositiveButton("Entendido",null).show();
                }
            }).create();
        dialog.setOnShowListener(v->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(RED));
        dialog.show();
    }
}
