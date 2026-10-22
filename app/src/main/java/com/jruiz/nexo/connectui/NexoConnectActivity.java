package com.jruiz.nexo.connectui;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.widget.*;
import android.os.Handler;
import java.util.*;
import com.jruiz.nexo.connect.vault.*;
import com.jruiz.nexo.data.DatabaseHelper;

/** Account access is scoped to this optional module, never to the Nexo launcher. */
public final class NexoConnectActivity extends NexoConnectPilotActivity {
    private boolean resumed,selectedHandled;
    private UUID conversation;
    private boolean invitationOpened;
    private LinearLayout messages;
    private TextView conversationTitle;
    private final Handler handler=new Handler(android.os.Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){if(conversation!=null&&!busy)syncChat();handler.postDelayed(this,10000);}};
    @Override protected boolean addHostSetupActions(){
        if(NexoConnectMigration.state(this).getBoolean("targetPending",false)){
            button("Continuar traslado",()->startActivity(new Intent(this,NexoConnectMigrationActivity.class)));return true;
        }
        try{if(NexoConnectChatBackup.owner(this)!=null)button("Conversaciones guardadas",this::archivedChats);}catch(Exception ignored){}
        label("Activa tu cuenta para hablar y compartir con otras personas. En Nexo te reconocerán por el nombre que elijas.",16);
        if(hasSavedPilotSession()){
            button("Entrar",this::resumeSavedSession);
            if(!resumed){resumed=true;resumeSavedSession();}
        }else{
            try{getPackageManager().getPackageInfo("com.jruiz.nexo.connect.pilot",0);
                button("Traer mi cuenta de Connect",()->startActivity(new Intent(this,NexoConnectMigrationActivity.class)));
            }catch(android.content.pm.PackageManager.NameNotFoundException absent){}
            button("Crear cuenta o entrar",this::openHostAccountSetup);
        }
        button("Seguir usando Nexo sin cuenta",this::finish);return true;
    }
    @Override protected void showHome(){
        conversation=null;
        if(stage!=READY){showSetup();return;}
        screen("Chat y compartir",false);
        String name=NexoConnectChatStore.ownName(this,identity.accountId());
        label(name.isEmpty()?"Elige cómo quieres que te conozcan":name,20);
        button("Chat",this::showChats);
        button("Compartir una ficha",this::chooseSection);
        button("Recibidos",()->{screen("Recibidos",true);button("Buscar nuevas fichas",this::showInbox);button("Ver fichas recibidas",this::showReceivedNotes);});
        button("Personas",this::people);
        button("Mi nombre",this::editName);
        button("Volver a Nexo",this::finish);
    }
    @Override protected void onVerifiedPilotReady(){
        if(NexoConnectChatStore.ownName(this,identity.accountId()).isEmpty()){editName();return;}
        String pending=getSharedPreferences("nexo-connect-pending-invite",0).getString("payload","");
        if(!pending.isEmpty()&&!invitationOpened){invitationOpened=true;acceptPendingInvitation(pending);return;}
        if(!selectedHandled&&getIntent().getLongExtra("nexoShareId",0)>0){selectedHandled=true;share(getIntent().getStringExtra("nexoShareSection"),getIntent().getLongExtra("nexoShareId",0));}
        else if(getIntent().getBooleanExtra("openChat",false))showChats();
    }
    @Override protected void onInvitationAccepted(){
        getSharedPreferences("nexo-connect-pending-invite",0).edit().remove("payload").apply();
        new AlertDialog.Builder(this).setTitle("Invitación aceptada")
            .setMessage("Para terminar, la otra persona debe añadir tu código y aceptar la conexión en Personas. Así los dos confirmáis con quién vais a compartir.")
            .setNegativeButton("Más tarde",null).setPositiveButton("Mostrar mi código",(d,w)->showOwnQr()).show();
    }
    @Override protected String contactName(AndroidPeerTrustStore.PeerPin p){return NexoConnectChatStore.peerName(this,identity.accountId(),p.peerId);}
    private void editName(){
        EditText name=new EditText(this);name.setHint("Por ejemplo, Javi");name.setText(NexoConnectChatStore.ownName(this,identity.accountId()));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("¿Cómo quieres que te conozcan?").setView(name).setPositiveButton("Guardar",null).setNegativeButton("Ahora no",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            try{NexoConnectChatStore.setOwnName(this,identity.accountId(),name.getText().toString());}
            catch(Exception invalid){name.setError("Escribe un nombre de hasta 50 caracteres");return;}
            dialog.dismiss();runWork(()->{for(var peer:trusted.listActive())NexoConnectChatTransport.send(this,client,identity,trusted,peer.peerId,"",true);return true;},out->{showHome();onVerifiedPilotReady();});
        }));dialog.show();
    }
    @Override protected void onInvitationCreated(String payload,android.graphics.Bitmap qr){
        label("Pendiente de aceptar",18);
        button("Compartir invitación",()->{
            try{
                java.io.File dir=new java.io.File(getCacheDir(),"connect-invites");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException();
                java.io.File image=new java.io.File(dir,UUID.randomUUID()+".nexo-invite");try(java.io.FileOutputStream out=new java.io.FileOutputStream(image)){out.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".connectinvites",image);
                Intent send=new Intent(Intent.ACTION_SEND).setType("application/vnd.nexo.invitation").putExtra(Intent.EXTRA_STREAM,uri)
                    .putExtra(Intent.EXTRA_TEXT,"Te invito a conectar conmigo en Nexo. Abre el archivo adjunto con Nexo. Te guiará para crear tu cuenta o entrar y aceptar la invitación. Si aún no tienes Nexo, pídeme la aplicación. La invitación dura una hora.")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(android.content.ClipData.newRawUri("Invitación Nexo",uri));startActivity(Intent.createChooser(send,"Enviar invitación"));
            }catch(Exception e){showError("No se pudo preparar la invitación.");}
        });
    }
    private void archivedChats(){
        conversation=null;screen("Conversaciones guardadas",false);button("Volver",this::showSetup);
        try{UUID owner=NexoConnectChatBackup.owner(this);if(owner==null)return;
            try(SQLiteDatabase db=NexoConnectMigration.inbox(this)){
                NexoConnectChatBackup.schema(db);NexoConnectChatStore.schema(db);
                try(android.database.Cursor cur=db.rawQuery("SELECT peer FROM nexo_chat_restored WHERE account=? UNION SELECT peer FROM nexo_chat_outgoing WHERE account=? UNION SELECT sender_id FROM nexo_connect_import_receipts WHERE entity_type='note'",new String[]{owner.toString(),owner.toString()})){
                    while(cur.moveToNext()){UUID peer=UUID.fromString(cur.getString(0));button(NexoConnectChatStore.peerName(this,owner,peer),()->{
                        screen(NexoConnectChatStore.peerName(this,owner,peer),false);button("Conversaciones",this::archivedChats);
                        try(SQLiteDatabase history=NexoConnectMigration.inbox(this)){for(var m:NexoConnectChatStore.messages(history,owner,peer))label((m.outgoing?"Tú: ":"")+m.text,17);}catch(Exception e){showError("No se pudo abrir el historial.");}
                    });}
                }
            }
        }catch(Exception e){showError("No se pudo abrir el historial.");}
    }
    private void people(){
        conversation=null;screen("Personas",true);
        button("Añadir con un código QR",this::openScanner);
        button("Mi código QR",this::showOwnQr);
        button("Abrir una imagen QR",this::openImageQr);
        button("Invitar a una persona",this::showInvite);
        label("La otra persona debe activar su cuenta y aceptar la conexión antes de recibir fichas o mensajes.",15);
        try{for(var p:trusted.listActive()){
            label(contactName(p),18);
            button("Aceptar conexión",()->runWork(()->{client.verifyStoredPeer(trusted,p.peerId);client.setReceiving(trusted,p.peerId,true);return true;},v->people()));
        }}catch(Exception e){showError("No se pudieron abrir tus personas.");}
    }
    private void showChats(){showChats(true);}
    private void showChats(boolean refresh){
        conversation=null;if(NexoConnectChatStore.ownName(this,identity.accountId()).isEmpty()){editName();return;}
        screen("Chat",true);
        try{var peers=trusted.listActive();if(peers.isEmpty())label("Añade a alguien para empezar a conversar.",16);
            for(var p:peers)button(contactName(p),()->openConversation(p.peerId));
        }catch(Exception e){showError("No se pudieron abrir las conversaciones.");}
        button("Añadir persona",this::people);
        if(refresh&&!busy)runWork(()->NexoConnectChatTransport.sync(this,client,identity,trusted,generation),v->showChats(false));
    }
    private void openConversation(UUID peer){
        conversation=peer;screen("Chat",true);conversationTitle=label(NexoConnectChatStore.peerName(this,identity.accountId(),peer),22);
        button("Todas las conversaciones",this::showChats);
        messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);column.addView(messages);
        EditText draft=new EditText(this);draft.setHint("Escribe un mensaje");draft.setMinLines(2);column.addView(draft);
        button("Enviar",()->{String text=draft.getText().toString().trim();if(text.isEmpty())return;
            if(text.length()>4000){draft.setError("El mensaje es demasiado largo");return;}
            draft.setText("");runWork(()->{NexoConnectChatTransport.send(this,client,identity,trusted,peer,text,false);return true;},v->renderMessages(peer));});
        button("Actualizar",this::syncChat);renderMessages(peer);syncChat();
    }
    private void renderMessages(UUID peer){
        if(!peer.equals(conversation)||messages==null)return;
        if(conversationTitle!=null)conversationTitle.setText(NexoConnectChatStore.peerName(this,identity.accountId(),peer));
        try(SQLiteDatabase db=NexoConnectMigration.inbox(this)){
            var all=NexoConnectChatStore.messages(db,identity.accountId(),peer);messages.removeAllViews();
            for(var m:all){TextView view=new TextView(this);view.setTextSize(17);view.setPadding(18,16,18,16);
                view.setText(m.text+"\n"+android.text.format.DateFormat.format("dd/MM HH:mm",m.time)+(m.outgoing?("sent".equals(m.status)?" · Enviado":" · No confirmado"):""));
                view.setGravity(m.outgoing?android.view.Gravity.END:android.view.Gravity.START);messages.addView(view);}
        }catch(Exception e){showError("No se pudo abrir el historial.");}
    }
    private void syncChat(){UUID peer=conversation;if(peer==null||busy)return;busy=true;
        new Thread(()->{try{refreshSessionIfNeeded();NexoConnectChatTransport.sync(this,client,identity,trusted,generation);}catch(Exception unavailable){}finally{runOnUiThread(()->{busy=false;if(!isFinishing()&&!isDestroyed())renderMessages(peer);});}},"nexo-chat-receive").start();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(poll);handler.postDelayed(poll,10000);}
    @Override protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    private void chooseSection(){new AlertDialog.Builder(this).setTitle("¿Qué quieres compartir?").setItems(NexoConnectHostShare.sections(),(d,i)->chooseEntry(NexoConnectHostShare.sectionKinds()[i])).show();}
    private void chooseEntry(String section){try(DatabaseHelper db=new DatabaseHelper(this)){
        var entries=NexoConnectHostShare.list(db,section);String[] names=new String[entries.size()];for(int i=0;i<names.length;i++)names[i]=entries.get(i).title;
        new AlertDialog.Builder(this).setTitle("Elige una ficha").setItems(names,(d,i)->share(section,entries.get(i).id)).show();
    }catch(Exception e){showError("No se pudieron abrir tus fichas.");}}
    private void share(String section,long id){try(DatabaseHelper db=new DatabaseHelper(this)){var draft=NexoConnectHostShare.prepare(db,section,id);offerHostEntity(draft.entity,draft.safeData);}catch(Exception e){showError("Esta ficha aún no puede enviarse desde Nexo. Puedes compartirla con otra aplicación.");}}
    @Override protected void handoffReceivedList(UUID delivery){
        new AlertDialog.Builder(this).setTitle("Añadir a Nexo").setMessage("Se guardará una copia de esta ficha en su sección.").setNegativeButton("Cancelar",null).setPositiveButton("Añadir",(d,w)->{
            try(SQLiteDatabase source=NexoConnectMigration.inbox(this);DatabaseHelper target=new DatabaseHelper(this)){
                var item=NexoConnectReceivedEntity.read(source,identity.accountId(),delivery,trusted);
                var result=com.jruiz.nexo.connect.NexoConnectPilotEntityImporter.copyApproved(target.getWritableDatabase(),identity.accountId(),delivery,item.sender,item.entity,item.dataCopy(),true);
                Toast.makeText(this,result.alreadyCopied?"La ficha ya estaba guardada":"Ficha añadida",Toast.LENGTH_LONG).show();
            }catch(Exception invalid){showError("No se pudo añadir esta ficha.");}
        }).show();
    }
}
