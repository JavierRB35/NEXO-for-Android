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
    private static final int PICK_PROFILE_PHOTO=7308;
    private UUID conversation;
    private boolean invitationOpened;
    private LinearLayout messages;
    private TextView conversationTitle;
    private ScrollView chatHistory;
    private boolean foreground;
    private String renderedMessages="";
    private final java.util.Map<UUID,String> drafts=new java.util.HashMap<>();
    private static final int INK=0xff142337,MUTED=0xff526177,GREEN=0xff0f766e,PAPER=0xfff7f9fc;
    private int px(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private android.graphics.drawable.GradientDrawable surface(int color,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;
    }
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private int personColor(UUID peer){int[] colors={GREEN,0xff305da8,0xff8c3978,0xff93530f,0xff4e6374};return colors[Math.floorMod(peer.hashCode(),colors.length)];}
    private TextView avatar(UUID peer,String name,int size){TextView v=text(name.isEmpty()?"?":name.substring(0,name.offsetByCodePoints(0,1)).toUpperCase(java.util.Locale.ROOT),size<30?11:18,android.graphics.Color.WHITE);v.setGravity(android.view.Gravity.CENTER);v.setBackground(surface(personColor(peer),size));v.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;}
    private android.view.View avatarView(UUID peer,String name,int size){
        if(identity!=null&&peer!=null){
            try{
                String photo=NexoConnectChatStore.peerAvatar(this,identity.accountId(),peer);
                if(!photo.isEmpty()){
                    android.graphics.Bitmap bitmap=NexoConnectProfileAvatar.decode(photo);
                    ImageView image=new ImageView(this);
                    image.setImageBitmap(bitmap);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    android.graphics.drawable.GradientDrawable round=new android.graphics.drawable.GradientDrawable();
                    round.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    round.setColor(0xffffffff);
                    image.setBackground(round);
                    image.setClipToOutline(true);
                    image.setContentDescription("Foto de "+name);
                    return image;
                }
            }catch(Exception invalid){/* local corrupt avatars never block Chat */}
        }
        return avatar(peer,name,size);
    }
    private Button action(String title,Runnable run){Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(GREEN);b.setBackground(surface(android.graphics.Color.WHITE,12));b.setMinHeight(px(48));b.setMinimumWidth(px(48));b.setOnClickListener(v->run.run());return b;}
    private LinearLayout chatShell(String title,UUID peer,Runnable back){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(PAPER);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);bar.setPadding(px(8),px(8),px(12),px(8));bar.setBackgroundColor(android.graphics.Color.WHITE);
        Button arrow=action("‹",back);arrow.setTextSize(28);arrow.setContentDescription("Volver a conversaciones");bar.addView(arrow,new LinearLayout.LayoutParams(px(48),px(48)));
        if(peer!=null){LinearLayout.LayoutParams av=new LinearLayout.LayoutParams(px(40),px(40));av.setMargins(px(4),0,px(12),0);bar.addView(avatarView(peer,title,40),av);}
        LinearLayout identityBox=new LinearLayout(this);identityBox.setOrientation(LinearLayout.VERTICAL);conversationTitle=text(title,20,INK);conversationTitle.setTypeface(null,android.graphics.Typeface.BOLD);identityBox.addView(conversationTitle);identityBox.addView(text(peer==null?"Tus conversaciones":"Nexo",12,MUTED));bar.addView(identityBox,new LinearLayout.LayoutParams(0,-2,1));
        if(peer!=null){
            Button rename=action("Editar",()->renamePeer(peer,()->openConversation(peer)));
            rename.setContentDescription("Editar el nombre de este contacto");
            bar.addView(rename,new LinearLayout.LayoutParams(px(72),px(48)));
        }
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));setContentView(root);column=root;return root;
    }

    private final Handler handler=new Handler(android.os.Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){if(conversation!=null&&!busy)syncChat();handler.postDelayed(this,10000);}};
    @Override protected boolean addHostSetupActions(){
        if(NexoConnectMigration.state(this).getBoolean("targetPending",false)){
            button("Continuar traslado",()->startActivity(new Intent(this,NexoConnectMigrationActivity.class)));return true;
        }
        if(hasSavedPilotSession()&&!resumed){
            resumed=true;
            LinearLayout root=chatShell("Chat",null,this::finish);
            TextView loading=text("Cargando conversaciones seguras…",16,MUTED);
            loading.setPadding(px(20),px(28),px(20),px(16));
            root.addView(loading);
            resumeSavedSession();
            return true;
        }
        try{if(NexoConnectChatBackup.owner(this)!=null)button("Conversaciones guardadas",this::archivedChats);}catch(Exception ignored){}
        label(hasSavedPilotSession()?"No se ha completado el acceso. Puedes intentarlo de nuevo sin borrar tus datos.":"Activa tu cuenta para hablar y compartir con otras personas.",16);
        if(hasSavedPilotSession()){
            button("Entrar / Continuar alta",this::resumeSavedSession);
            button("Volver a verificar mi correo",this::openHostEmailReverification);
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
        if(!selectedHandled&&getIntent()!=null){
            String action=getIntent().getStringExtra("nexoConnectAction");
            if("profile".equals(action)){selectedHandled=true;showProfile();return;}
            if("generate".equals(action)){selectedHandled=true;showInvite();return;}
            if("pair".equals(action)){selectedHandled=true;openScanner();return;}
            if(getIntent().getBooleanExtra("openChat",false)){
                selectedHandled=true;showChats();return;
            }
        }
        screen("Chat y compartir",false);
        String name=NexoConnectChatStore.ownName(this,identity.accountId());
        label(name.isEmpty()?"Elige cómo quieres que te conozcan":name,20);
        button("Chat",this::showChats);
        button("Compartir una ficha",this::chooseSection);
        button("Recibidos",()->{screen("Recibidos",true);button("Buscar nuevas fichas",this::showInbox);button("Ver fichas recibidas",this::showReceivedNotes);});
        button("Personas",this::people);
        button("Mi perfil",this::showProfile);
        button("Volver a Nexo",this::finish);
    }
    @Override protected void onVerifiedPilotReady(){
        if(NexoConnectChatStore.ownName(this,identity.accountId()).isEmpty()){editName();return;}
        String pending=getSharedPreferences("nexo-connect-pending-invite",0).getString("payload","");
        if(!pending.isEmpty()&&!invitationOpened){invitationOpened=true;acceptPendingInvitation(pending);return;}
        if(!selectedHandled&&getIntent().getLongExtra("nexoShareId",0)>0){selectedHandled=true;share(getIntent().getStringExtra("nexoShareSection"),getIntent().getLongExtra("nexoShareId",0));}
        // Direct Chat/Profile entry already rendered by showHome; no intermediate page.
    }
    @Override protected void onInvitationAccepted(){
        getSharedPreferences("nexo-connect-pending-invite",0).edit().remove("payload").apply();
        broadcastProfile();
        new AlertDialog.Builder(this).setTitle("Contacto añadido")
            .setMessage("La conexión se ha confirmado en este teléfono. Para poder compartir en ambas direcciones, la otra persona debe comprobar también tu código desde Nexo → Personas.")
            .setNegativeButton("Ahora no",null).setPositiveButton("Ver mi código",(d,w)->showOwnQr()).show();
    }
    @Override protected String contactName(AndroidPeerTrustStore.PeerPin p){return NexoConnectChatStore.peerName(this,identity.accountId(),p.peerId);}
    @Override protected android.view.View contactAvatar(AndroidPeerTrustStore.PeerPin p){
        return avatarView(p.peerId,contactName(p),42);
    }
    private void broadcastProfile(){
        if(stage!=READY||identity==null||trusted==null||client==null)return;
        new Thread(()->{
            try{
                synchronized(sessionRefreshLock()){
                    refreshSessionIfNeeded();
                    NexoConnectChatTransport.syncProfiles(this,client,identity,trusted);
                }
            }catch(Exception pending){/* next Chat opening retries without losing the saved profile */}
        },"nexo-connect-profile-share").start();
    }
    private void editName(){
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Por ejemplo, Mar");name.setText(NexoConnectChatStore.ownName(this,identity.accountId()));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Tu nombre en Nexo")
            .setMessage("Tus contactos verificados verán este nombre en sus conversaciones.")
            .setView(name).setPositiveButton("Guardar",null).setNegativeButton("Ahora no",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            try{NexoConnectChatStore.setOwnName(this,identity.accountId(),name.getText().toString());}
            catch(Exception invalid){name.setError("Escribe un nombre de hasta 50 caracteres");return;}
            dialog.dismiss();broadcastProfile();showHome();
        }));dialog.show();
    }
    private void showProfile(){
        if(stage!=READY||identity==null){showSetup();return;}
        conversation=null;screen("Mi perfil de Nexo",true);
        label("Elige cómo te verán las personas con las que conectes. Tu nombre y foto se envían cifrados solo a contactos verificados.",16);
        String encoded=NexoConnectChatStore.ownAvatar(this,identity.accountId());
        LinearLayout preview=new LinearLayout(this);preview.setGravity(android.view.Gravity.CENTER_VERTICAL);
        preview.setPadding(px(12),px(12),px(12),px(12));
        preview.setBackground(surface(0xffffffff,16));
        column.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        if(encoded.isEmpty()){
            String name=NexoConnectChatStore.ownName(this,identity.accountId());
            preview.addView(avatar(identity.accountId(),name.isEmpty()?"?":name,64),
                    new LinearLayout.LayoutParams(px(64),px(64)));
        }else{
            try{
                ImageView img=new ImageView(this);
                img.setImageBitmap(NexoConnectProfileAvatar.decode(encoded));
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                android.graphics.drawable.GradientDrawable mask=new android.graphics.drawable.GradientDrawable();
                mask.setShape(android.graphics.drawable.GradientDrawable.OVAL);mask.setColor(0xffffffff);
                img.setBackground(mask);img.setClipToOutline(true);
                preview.addView(img,new LinearLayout.LayoutParams(px(64),px(64)));
            }catch(Exception invalid){preview.addView(avatar(identity.accountId(), "?",64));}
        }
        TextView hint=text("Fotografía opcional",15,MUTED);hint.setPadding(px(14),0,0,0);
        preview.addView(hint);
        button("Elegir fotografía",()->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
            try{startActivityForResult(pick,PICK_PROFILE_PHOTO);}
            catch(Exception unavailable){showError("No se pudo abrir la galería.");}
        });
        if(!encoded.isEmpty())button("Quitar fotografía",()->{
            try{NexoConnectChatStore.setOwnAvatar(this,identity.accountId(),"");showProfile();broadcastProfile();}
            catch(Exception invalid){showError("No se pudo quitar la foto.");}
        });
        label("Nombre visible",16);
        EditText name=new EditText(this);
        name.setSingleLine(true);name.setTextSize(17);name.setHint("Cómo quieres que te conozcan");
        name.setText(NexoConnectChatStore.ownName(this,identity.accountId()));
        column.addView(name,new LinearLayout.LayoutParams(-1,-2));
        button("Guardar mi perfil",()->{
            try{NexoConnectChatStore.setOwnName(this,identity.accountId(),name.getText().toString());}
            catch(Exception invalid){name.setError("Escribe entre 1 y 50 caracteres");return;}
            android.widget.Toast.makeText(this,"Perfil guardado. Se compartirá con tus contactos.",Toast.LENGTH_LONG).show();
            showProfile();broadcastProfile();
        });
        button("Volver a Nexo",this::finish);
    }
    private void renamePeer(UUID peer){renamePeer(peer,this::people);}
    private void renamePeer(UUID peer,Runnable afterSave){
        try{
            if(identity==null||trusted==null||trusted.findActive(peer)==null)return;
        }catch(Exception invalid){showError("No se puede verificar este contacto.");return;}
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setHint("Nombre para ti");
        input.setText(getSharedPreferences("nexo-connect-local-names",0).getString(identity.accountId()+"_"+peer,""));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Cambiar nombre del contacto")
            .setMessage("Es un alias privado de tu teléfono. La otra persona conserva su nombre y su identidad verificada.")
            .setView(input).setPositiveButton("Guardar",null)
            .setNegativeButton("Cancelar",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(button->{
            try{NexoConnectChatStore.setLocalAlias(this,identity.accountId(),peer,input.getText().toString());}
            catch(Exception e){input.setError("Nombre de hasta 50 caracteres");return;}
            d.dismiss();afterSave.run();
        }));d.show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=PICK_PROFILE_PHOTO){super.onActivityResult(requestCode,resultCode,data);return;}
        if(resultCode!=RESULT_OK||data==null||data.getData()==null){showProfile();return;}
        if(identity==null||stage!=READY){showSetup();return;}
        final UUID owner=identity.accountId();
        final android.net.Uri uri=data.getData();
        new Thread(()->{
            String error=null;
            try{
                String photo=NexoConnectProfileAvatar.importFrom(getContentResolver(),uri);
                NexoConnectChatStore.setOwnAvatar(this,owner,photo);
            }catch(Exception problem){error="No se ha podido guardar esta fotografía. Prueba con otra imagen.";}
            final String message=error;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                showProfile();
                if(message==null)broadcastProfile();
                else new AlertDialog.Builder(this).setTitle("Fotografía").setMessage(message)
                    .setPositiveButton("Entendido",null).show();
            });
        },"nexo-profile-image").start();
    }
    @Override protected void onInvitationCreated(String payload,android.graphics.Bitmap qr){
        label("Comparte esta invitación por WhatsApp o en persona. La otra persona abrirá el archivo con Nexo.",16);
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
        conversation=null;screen("Nexo Connect",true);
        label("Conecta con otra persona de forma segura. Comprueba su código de 16 caracteres por teléfono o en persona.",16);
        button("Generar vinculación",this::showInvite);
        button("Vincular dispositivo",this::openScanner);
        button("Abrir invitación recibida",this::openImageQr);
        button("Mi código de seguridad",this::showOwnQr);
        try{
            var peers=trusted.listActive();
            label(peers.isEmpty()?"Aún no hay personas guardadas.":"Mis personas",19);
            for(var p:peers){
                LinearLayout person=new LinearLayout(this);
                person.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,-2);
                item.bottomMargin=px(8);
                person.setPadding(px(10),px(8),px(10),px(8));
                person.setBackground(surface(0xffffffff,14));
                column.addView(person,item);
                person.addView(avatarView(p.peerId,contactName(p),40),new LinearLayout.LayoutParams(px(40),px(40)));
                TextView contact=text(contactName(p),17,INK);
                contact.setTypeface(null,android.graphics.Typeface.BOLD);
                contact.setPadding(px(12),0,0,0);
                person.addView(contact,new LinearLayout.LayoutParams(0,-2,1));
                Button alias=action("Editar nombre",()->renamePeer(p.peerId));
                person.addView(alias);
                button("Comprobar conexión con "+contactName(p),()->runWork(()->{
                    client.verifyStoredPeer(trusted,p.peerId);
                    client.setReceiving(trusted,p.peerId,true);
                    return true;
                },v->{broadcastProfile();new AlertDialog.Builder(this)
                    .setTitle("Contacto preparado")
                    .setMessage("La identidad coincide y este teléfono puede recibir sus mensajes y fichas. Para compartir también desde el otro móvil, debe confirmar tu código.")
                    .setPositiveButton("Entendido",(d,w)->people()).show();}));
            }
        }catch(Exception e){showError("No se pudieron abrir tus personas.");}
    }
    private void showChats(){showChats(true);}
    // The local chat-history controls preserve identity and contact pins.
    private void showChats(boolean refresh){
        conversation=null;chatHistory=null;if(NexoConnectChatStore.ownName(this,identity.accountId()).isEmpty()){editName();return;}
        LinearLayout root=chatShell("Chat",null,this::finish);ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(px(16),px(16),px(16),px(16));scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        try(SQLiteDatabase db=NexoConnectMigration.inbox(this)){var peers=trusted.listActive();if(peers.isEmpty())list.addView(text("Añade a alguien para empezar a conversar.",16,MUTED));
            for(var p:peers){String name=contactName(p);var history=NexoConnectChatStore.messages(db,identity.accountId(),p.peerId);var last=history.isEmpty()?null:history.get(history.size()-1);
                LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(px(12),px(12),px(12),px(12));row.setBackground(surface(android.graphics.Color.WHITE,16));LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.bottomMargin=px(8);list.addView(row,rowParams);
                row.addView(avatarView(p.peerId,name,48),new LinearLayout.LayoutParams(px(48),px(48)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(px(12),0,0,0);TextView title=text(name,16,INK);title.setTypeface(null,android.graphics.Typeface.BOLD);copy.addView(title);TextView preview=text(last==null?"Empieza la conversación":(last.outgoing?"Tú: ":"")+last.text,14,MUTED);preview.setMaxLines(2);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(preview);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
                row.setContentDescription(name+(last==null?"":", "+preview.getText()));row.setFocusable(true);row.setOnClickListener(v->openConversation(p.peerId));
            }
        }catch(Exception e){showError("No se pudieron abrir las conversaciones.");}
        Button add=action("Añadir persona",this::people);root.addView(add,new LinearLayout.LayoutParams(-1,px(56)));
        if(refresh&&!busy)runWork(()->{
            NexoConnectChatTransport.syncProfiles(this,client,identity,trusted);
            return NexoConnectChatTransport.sync(this,client,identity,trusted,generation);
        },v->{if(conversation==null)showChats(false);});
    }
    private void openConversation(UUID peer){
        conversation=peer;renderedMessages="";String name=NexoConnectChatStore.peerName(this,identity.accountId(),peer);LinearLayout root=chatShell(name,peer,this::showChats);
        chatHistory=new ScrollView(this);chatHistory.setFillViewport(true);chatHistory.setBackgroundColor(0xfff6f2e9);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(px(12),px(12),px(12),px(12));chatHistory.addView(messages);root.addView(chatHistory,new LinearLayout.LayoutParams(-1,0,1));chatHistory.setOnScrollChangeListener((v,x,y,ox,oy)->markVisibleRead());
        LinearLayout compose=new LinearLayout(this);compose.setGravity(android.view.Gravity.BOTTOM);compose.setPadding(px(8),px(8),px(8),px(8));compose.setBackgroundColor(android.graphics.Color.WHITE);
        EditText draft=new EditText(this);draft.setHint("Escribe un mensaje…");draft.setTextColor(INK);draft.setHintTextColor(MUTED);draft.setTextSize(16);draft.setMinHeight(px(48));draft.setMaxLines(5);draft.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);draft.setBackground(surface(PAPER,16));draft.setPadding(px(12),px(10),px(12),px(10));draft.setText(drafts.getOrDefault(peer,""));draft.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence a,int st,int count,int after){}public void onTextChanged(CharSequence value,int st,int before,int count){drafts.put(peer,value.toString());}public void afterTextChanged(android.text.Editable e){}});
        compose.addView(draft,new LinearLayout.LayoutParams(0,-2,1));
        Button send=action("➤",()->{if(busy){android.widget.Toast.makeText(this,"Actualizando conversación. Espera un momento.",android.widget.Toast.LENGTH_SHORT).show();return;}String value=draft.getText().toString().trim();if(value.isEmpty())return;if(value.length()>4000){draft.setError("El mensaje es demasiado largo");return;}
            runWork(()->{NexoConnectChatTransport.send(this,client,identity,trusted,peer,value,false);return true;},v->{draft.setText("");if(peer.equals(conversation)){renderMessages(peer);chatHistory.post(()->chatHistory.fullScroll(android.view.View.FOCUS_DOWN));}});});send.setContentDescription("Enviar mensaje");send.setTextColor(android.graphics.Color.WHITE);send.setBackground(surface(GREEN,24));LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(px(48),px(48));sendParams.leftMargin=px(8);compose.addView(send,sendParams);root.addView(compose,new LinearLayout.LayoutParams(-1,-2));
        renderMessages(peer);syncChat();
    }
    private void renderMessages(UUID peer){
        if(!peer.equals(conversation)||messages==null||chatHistory==null)return;
        String name=NexoConnectChatStore.peerName(this,identity.accountId(),peer);conversationTitle.setText(name);
        try(SQLiteDatabase db=NexoConnectMigration.inbox(this)){
            var all=NexoConnectChatStore.messages(db,identity.accountId(),peer);StringBuilder stamp=new StringBuilder();for(var m:all)stamp.append(m.delivery).append(m.status);if(stamp.toString().equals(renderedMessages)&&messages.getChildCount()>0){markVisibleRead();return;}
            boolean atBottom=messages.getChildCount()==0||chatHistory.getScrollY()+chatHistory.getHeight()>=messages.getHeight()-px(64);int previousY=chatHistory.getScrollY();renderedMessages=stamp.toString();messages.removeAllViews();String date="";
            if(all.isEmpty()){TextView empty=text("Empieza la conversación con "+name+".",16,MUTED);empty.setGravity(android.view.Gravity.CENTER);empty.setPadding(px(16),px(40),px(16),px(40));messages.addView(empty);}
            for(var m:all){String day=android.text.format.DateFormat.format("dd MMM yyyy",m.time).toString();if(!day.equals(date)){TextView separator=text(day,12,MUTED);separator.setGravity(android.view.Gravity.CENTER);separator.setPadding(0,px(12),0,px(12));messages.addView(separator);date=day;}
                LinearLayout row=new LinearLayout(this);row.setGravity(m.outgoing?android.view.Gravity.END:android.view.Gravity.START);LinearLayout bubble=new LinearLayout(this);bubble.setOrientation(LinearLayout.VERTICAL);bubble.setPadding(px(12),px(10),px(12),px(8));bubble.setBackground(surface(m.outgoing?0xfffff4d6:android.graphics.Color.WHITE,16));
                TextView content=text(m.text,16,INK);content.setTextIsSelectable(true);content.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*.78));bubble.addView(content);LinearLayout meta=new LinearLayout(this);meta.setGravity(android.view.Gravity.END|android.view.Gravity.CENTER_VERTICAL);meta.setPadding(0,px(6),0,0);
                if(m.outgoing&&"read".equals(m.status)){TextView reader=avatar(peer,name,22);reader.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);reader.setContentDescription("Leído por "+name);reader.setTooltipText("Leído por "+name);LinearLayout.LayoutParams readerParams=new LinearLayout.LayoutParams(px(22),px(22));readerParams.rightMargin=px(6);meta.addView(reader,readerParams);}
                String stateLabel=m.outgoing&&!"read".equals(m.status)?("sent".equals(m.status)?" · Enviado":" · No confirmado"):"";meta.addView(text(android.text.format.DateFormat.format("HH:mm",m.time)+stateLabel,12,MUTED));bubble.addView(meta);
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.setMargins(m.outgoing?px(40):0,px(4),m.outgoing?0:px(40),px(4));row.addView(bubble,bp);if(!m.outgoing)row.setTag(m.delivery);messages.addView(row);
            }
            chatHistory.post(()->{if(!peer.equals(conversation)||chatHistory==null)return;if(atBottom)chatHistory.fullScroll(android.view.View.FOCUS_DOWN);else chatHistory.scrollTo(0,previousY);markVisibleRead();});
        }catch(Exception e){showError("No se pudo abrir el historial.");}
    }
    private void markVisibleRead(){
        if(!foreground||!hasWindowFocus()||conversation==null||messages==null||chatHistory==null||identity==null)return;
        boolean changed=false;android.graphics.Rect viewport=new android.graphics.Rect();if(!chatHistory.getGlobalVisibleRect(viewport))return;
        try(SQLiteDatabase db=NexoConnectMigration.inbox(this)){for(int i=0;i<messages.getChildCount();i++){android.view.View row=messages.getChildAt(i);if(!(row.getTag() instanceof String))continue;android.graphics.Rect bounds=new android.graphics.Rect();if(row.getGlobalVisibleRect(bounds)&&bounds.intersect(viewport)&&bounds.height()>=Math.min(row.getHeight(),px(48)))changed|=NexoConnectChatStore.queueRead(db,identity.accountId(),conversation,(String)row.getTag());}}catch(Exception ignored){}
        if(changed)handler.postDelayed(()->{if(foreground&&conversation!=null)syncChat();},500);
    }
    private void syncChat(){UUID peer=conversation;if(peer==null||busy)return;busy=true;
        new Thread(()->{try{synchronized(sessionRefreshLock()){refreshSessionIfNeeded();NexoConnectChatTransport.sync(this,client,identity,trusted,generation);}}catch(Exception unavailable){}finally{runOnUiThread(()->{busy=false;if(!isFinishing()&&!isDestroyed())renderMessages(peer);});}},"nexo-chat-receive").start();
    }
    @Override protected void onResume(){super.onResume();foreground=true;handler.removeCallbacks(poll);handler.postDelayed(poll,10000);markVisibleRead();}
    @Override protected void onPause(){foreground=false;handler.removeCallbacks(poll);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)markVisibleRead();}
    @Override public void onBackPressed(){if(conversation!=null)showChats();else super.onBackPressed();}
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
