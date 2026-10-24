'use strict';
/* Mis vehículos: interfaz local, sin acceso a la red y sin catálogos remotos. */
(function () {
  const CATALOG = [
    {title:'Aceite y filtros',items:['Cambio de aceite del motor','Filtro de aceite','Filtro de aire del motor','Filtro de habitáculo / polen','Filtro de combustible']},
    {title:'Frenos y ruedas',items:['Pastillas de freno','Discos de freno','Neumáticos','Líquido de frenos']},
    {title:'Motor y otros',items:['Batería','Bujías','Correa de distribución','Correa de accesorios','Líquido refrigerante','Escobillas limpiaparabrisas','Revisión general','ITV']}
  ];
  const base = new Set(CATALOG.flatMap(group => group.items).map(s => s.toLocaleLowerCase('es')));
  const native = () => window.NexoNative;
  const parse = (raw, fallback) => {try {return JSON.parse(raw);} catch (_) {return fallback;}};
  const html = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch =>
    ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const n = value => Number(value) || 0;
  const dateNow = () => {
    const d = new Date();
    return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
  };
  const formatDate = iso => /^\d{4}-\d{2}-\d{2}$/.test(String(iso || ''))
    ? iso.slice(8,10)+'/'+iso.slice(5,7)+'/'+iso.slice(0,4) : '';
  const formatKm = km => Number(km).toLocaleString('es-ES')+' km';
  const money = cents => cents == null ? '' : (Number(cents)/100).toLocaleString('es-ES',{style:'currency',currency:'EUR'});
  const read = method => parse(native()[method](),[]);
  const message = text => window.toast(text);
  let currentId = 0;
  let draft = null;
  let fileNonce = 0;
  let activeRequest = '';
  let customCache = [];

  function vehicles(){ return read('getVehicles'); }
  function services(id){ return parse(native().getVehicleServices(id),[]); }
  function rules(id){ return parse(native().getVehicleDueRules(id),[]); }
  function custom(){ return read('getVehicleOperations'); }
  function getVehicle(id){ return vehicles().find(v => n(v.id) === n(id)); }
  function respond(raw){const result = parse(raw,{}); if(!result.ok) message(result.error || 'No se pudo guardar.');return result;}
  function stopDraft(){
    if(draft && draft.file && !draft.saved && draft.file.path) {
      try {native().discardDocumentFile(draft.file.path);} catch (_) {}
    }
    draft=null;
    activeRequest='';
  }
  function showModal(title, body){
    window.openModal(title,body);
  }
  function close(){
    window.closeModal(true);
  }
  function confirmed(title, description, action){
    window.confirmGeneric(title,description,action,'Eliminar');
  }
  function selectedId(){
    const rows=vehicles();
    if(!rows.length)return 0;
    if(!rows.some(v=>n(v.id)===currentId)) currentId=n(rows[0].id);
    return currentId;
  }

  function dueStatus(rule,vehicle){
    const byDate = rule.dueDate ? rule.dueDate.localeCompare(dateNow()) : 1;
    const remaining = rule.dueMileage == null ? null : n(rule.dueMileage)-n(vehicle.mileage);
    const overdue = byDate<0 || (remaining!==null && remaining<0);
    const today = byDate===0 || remaining===0;
    if(overdue||today)return {cls:'vehicle-due--late',name:overdue?'Vencido':'Corresponde ahora'};
    const soonDate = rule.dueDate && rule.dueDate<=new Date(Date.now()+30*86400000).toLocaleDateString('sv-SE');
    const soonKm = remaining!==null && remaining<=1000;
    if(soonDate || soonKm)return {cls:'vehicle-due--soon',name:'Próximo'};
    return {cls:'',name:'Programado'};
  }
  function dueDetails(rule){
    return [rule.dueDate ? formatDate(rule.dueDate) : '',rule.dueMileage!=null ? formatKm(rule.dueMileage) : ''].filter(Boolean).join(' · ');
  }
  function render(){
    const root=document.getElementById('panelContent');
    if(!root || window.state.screen!=='panel' || window.state.lastPanel!=='vehicles')return;
    const list=vehicles(),id=selectedId(),vehicle=list.find(v=>n(v.id)===id);
    const action=document.getElementById('panelAction');
    if(action)action.innerHTML='<button class="secondary-button" type="button" id="vehicleNew">+ Vehículo</button>';
    document.getElementById('vehicleNew')?.addEventListener('click',()=>openVehicle());
    if(!vehicle){
      root.innerHTML='<div class="vehicle-empty"><p class="vehicle-empty-icon">🚘</p><h2>Registra tu primer coche</h2><p>Solo tendrás que indicar un nombre y sus kilómetros. Después podrás registrar mantenimientos marcando casillas.</p><button class="primary-button" id="vehicleFirst" type="button">Añadir vehículo</button></div>';
      root.querySelector('#vehicleFirst').addEventListener('click',()=>openVehicle());
      window.updateFloatingActions?.();return;
    }
    const histories=services(id),due=rules(id);
    const latest=histories[0];
    root.innerHTML=
      '<div class="vehicle-select-line"><label for="vehicleSelect">Vehículo</label>'+
      '<select id="vehicleSelect">'+list.map(v=>'<option value="'+n(v.id)+'"'+(n(v.id)===id?' selected':'')+'>'+html(v.name+(v.plate?' · '+v.plate:''))+'</option>').join('')+'</select>'+
      '<button type="button" class="secondary-button" id="vehicleEdit" aria-label="Editar vehículo">Editar</button></div>'+
      '<section class="vehicle-summary"><div class="vehicle-summary-top"><div><span class="vehicle-eyebrow">Kilometraje conocido</span><strong>'+formatKm(vehicle.mileage)+'</strong><small>'+html(vehicle.name)+(vehicle.plate?' · '+html(vehicle.plate):'')+'</small></div><div class="vehicle-summary-icon">🚗</div></div>'+
      '<div class="vehicle-actions"><button class="primary-button" id="vehicleAddService" type="button">+ Mantenimiento</button><button class="secondary-button" id="vehicleUpdateKm" type="button">Actualizar km</button></div></section>'+
      '<div class="vehicle-section-heading"><h2>Próximas revisiones</h2><button class="vehicle-link" type="button" id="vehicleAddDue">+ Aviso</button></div>'+
      '<div class="vehicle-due-list">'+(due.length?due.map(rule=>{const status=dueStatus(rule,vehicle);return '<div class="vehicle-due '+status.cls+'"><button type="button" data-vehicle-due="'+html(rule.operation)+'"><strong>'+html(rule.operation)+'</strong><small>'+html(dueDetails(rule))+'</small></button><span>'+status.name+'</span></div>';}).join(''):'<p class="vehicle-hint">Sin revisiones programadas. Puedes definir fechas o kilómetros cuando quieras.</p>')+'</div>'+
      '<div class="vehicle-section-heading"><h2>Historial de mantenimiento</h2><button class="vehicle-link" id="vehicleCatalog" type="button">Mis operaciones</button></div>'+
      (latest?'<button class="secondary-button vehicle-repeat" type="button" id="vehicleRepeat">↻ Repetir último mantenimiento</button>':'')+
      '<div class="vehicle-history">'+(histories.length?histories.map(row=>
        '<article class="vehicle-service-card"><button class="vehicle-service-main" type="button" data-vehicle-service="'+n(row.id)+'"><strong>'+html((row.operations||[]).join(' · '))+'</strong><small>'+formatDate(row.date)+(row.mileage==null?'':' · '+formatKm(row.mileage))+'</small></button>'+
        (row.costCents==null?'':'<span class="vehicle-cost">'+money(row.costCents)+'</span>')+'</article>').join(''):'<p class="vehicle-hint">Todavía no hay mantenimientos registrados.</p>')+'</div>'+
      '<p class="vehicle-hint">Los avisos por kilómetros se calculan con el kilometraje que introduzcas. No se leen automáticamente del vehículo.</p>';
    root.querySelector('#vehicleSelect').addEventListener('change',e=>{currentId=n(e.target.value);render();});
    root.querySelector('#vehicleEdit').addEventListener('click',()=>openVehicle(id));
    root.querySelector('#vehicleUpdateKm').addEventListener('click',()=>openMileage(id));
    root.querySelector('#vehicleAddService').addEventListener('click',()=>openService(id));
    root.querySelector('#vehicleAddDue').addEventListener('click',()=>openDue(id));
    root.querySelector('#vehicleCatalog').addEventListener('click',manageCatalog);
    root.querySelector('#vehicleRepeat')?.addEventListener('click',()=>openService(id,0,latest));
    root.querySelectorAll('[data-vehicle-service]').forEach(button=>button.addEventListener('click',()=>openService(id,n(button.dataset.vehicleService))));
    root.querySelectorAll('[data-vehicle-due]').forEach(button=>button.addEventListener('click',()=>openDue(id,button.dataset.vehicleDue)));
    window.updateFloatingActions?.();
  }

  function openVehicle(id=0){
    const vehicle=id?getVehicle(id):null;
    showModal(vehicle?'Editar vehículo':'Nuevo vehículo',
      '<form id="vehicleForm" class="vehicle-form"><label>Nombre del vehículo<input id="vehicleName" maxlength="80" required placeholder="Ej. Coche familiar" value="'+html(vehicle?.name||'')+'"></label>'+
      '<label>Matrícula (opcional)<input id="vehiclePlate" maxlength="24" value="'+html(vehicle?.plate||'')+'"></label>'+
      '<label>Kilometraje actual<input id="vehicleMileage" type="number" min="0" max="10000000" step="1" required value="'+n(vehicle?.mileage)+'"></label>'+
      '<div class="vehicle-form-actions"><button class="primary-button" type="submit">Guardar vehículo</button>'+
      (vehicle?'<button class="danger-button" type="button" id="vehicleDelete">Eliminar vehículo</button>':'')+'</div></form>');
    document.getElementById('vehicleForm').addEventListener('submit',event=>{
      event.preventDefault();
      const name=document.getElementById('vehicleName').value.trim();
      const km=Number(document.getElementById('vehicleMileage').value);
      if(!name||!Number.isInteger(km)||km<0){message('Revisa el nombre y los kilómetros.');return;}
      const result=respond(native().saveVehicle(JSON.stringify({id:n(id),name,plate:document.getElementById('vehiclePlate').value,mileage:km})));
      if(result.ok){currentId=n(result.id);close();render();message('Vehículo guardado.');}
    });
    document.getElementById('vehicleDelete')?.addEventListener('click',()=>{
      confirmed('Eliminar vehículo','Se borrarán todas sus actuaciones y sus avisos. Esta acción no se puede deshacer.',()=>{
        if(native().deleteVehicle(id)){close();currentId=0;render();message('Vehículo eliminado.');}
        else message('No se pudo eliminar el vehículo.');
      });
    });
  }
  function openMileage(id){
    const v=getVehicle(id);if(!v)return;
    showModal('Actualizar kilometraje',
      '<form id="vehicleMileageForm" class="vehicle-form"><p>Último valor registrado: <strong>'+formatKm(v.mileage)+'</strong>.</p><label>Kilómetros actuales<input id="vehicleNewMileage" type="number" min="0" max="10000000" step="1" required value="'+n(v.mileage)+'"></label><button class="primary-button" type="submit">Guardar kilómetros</button></form>');
    document.getElementById('vehicleMileageForm').addEventListener('submit',e=>{
      e.preventDefault();const mileage=Number(document.getElementById('vehicleNewMileage').value);
      if(!Number.isInteger(mileage)||mileage<0||mileage>10000000){message('Kilometraje no válido.');return;}
      if(mileage<n(v.mileage) && !window.confirm('Estás reduciendo el kilometraje conocido. ¿Confirmas la corrección?'))return;
      const result=respond(native().saveVehicle(JSON.stringify({id,name:v.name,plate:v.plate,mileage})));
      if(result.ok){close();render();message('Kilometraje actualizado.');}
    });
  }

  function getOperations(){ customCache=custom();return CATALOG.map(group=>({title:group.title,items:group.items.map(label=>({label}))})).concat(
    customCache.length?[{title:'Mis operaciones',items:customCache.filter(op=>!base.has(String(op.label).toLocaleLowerCase('es')))}]:[]);
  }
  function captureDraft(){
    if(!draft)return;
    const date=document.getElementById('vehicleServiceDate');
    if(!date)return;
    draft.date=date.value;
    draft.mileage=document.getElementById('vehicleServiceKm').value;
    draft.cost=document.getElementById('vehicleServiceCost').value;
    draft.selection=new Set([...document.querySelectorAll('[data-v-operation]:checked')].map(ch=>ch.value));
  }
  function operationCheckbox(op){
    const checked=draft.selection.has(op.label);
    return '<label class="vehicle-check"><input type="checkbox" data-v-operation value="'+html(op.label)+'"'+(checked?' checked':'')+'><span>'+html(op.label)+'</span></label>';
  }
  function renderServiceForm(){
    const groups=getOperations();
    const root=document.getElementById('modalBody');
    if(!root||!draft)return;
    root.innerHTML='<form class="vehicle-form vehicle-service-form" id="vehicleServiceForm">'+
      '<div class="vehicle-inline-fields"><label>Fecha<input id="vehicleServiceDate" type="date" required value="'+html(draft.date)+'"></label>'+
      '<label>Kilómetros (opcional)<input id="vehicleServiceKm" type="number" min="0" max="10000000" step="1" placeholder="Sin indicar" value="'+html(draft.mileage)+'"></label></div>'+
      '<p class="vehicle-form-heading">¿Qué se ha hecho?</p>'+
      groups.map(group=>'<fieldset class="vehicle-check-group"><legend>'+html(group.title)+'</legend>'+group.items.map(operationCheckbox).join('')+'</fieldset>').join('')+
      '<div class="vehicle-custom-entry"><label for="vehicleNewOperation">Añadir operación reutilizable</label><div class="vehicle-custom-row"><input id="vehicleNewOperation" maxlength="90" placeholder="Ej. Sensor de temperatura"><button id="vehicleCustomAdd" class="secondary-button" type="button">Añadir</button></div></div>'+
      '<div class="vehicle-inline-fields"><label>Coste total (€) · opcional<input id="vehicleServiceCost" type="number" min="0" max="1000000" step="0.01" inputmode="decimal" placeholder="0,00" value="'+html(draft.cost)+'"></label></div>'+
      '<div class="vehicle-file-row"><button id="vehicleAttach" class="secondary-button" type="button">'+(draft.file?.path?'Cambiar factura':'Adjuntar factura')+'</button>'+
      (draft.file?.path?'<button id="vehicleViewFile" class="vehicle-link" type="button">Ver</button><button id="vehicleRemoveFile" class="vehicle-link" type="button">Quitar</button><small>'+html(draft.file.name||'Factura adjunta')+'</small>':'<small>Fotografía o PDF · opcional</small>')+'</div>'+
      '<div class="vehicle-form-actions"><button class="primary-button" type="submit">Guardar mantenimiento</button>'+
      (draft.id?'<button class="danger-button" type="button" id="vehicleRemoveService">Eliminar actuación</button>':'')+'</div></form>';
    document.getElementById('vehicleCustomAdd').addEventListener('click',addOperation);
    document.getElementById('vehicleServiceForm').addEventListener('submit',saveService);
    document.getElementById('vehicleAttach').addEventListener('click',()=>{
      captureDraft();activeRequest='vehicle-'+(++fileNonce)+'-'+Date.now();
      native().chooseDocumentFile(activeRequest);
    });
    document.getElementById('vehicleViewFile')?.addEventListener('click',()=>native().openDocumentFile(draft.file.path,draft.file.name,draft.file.mime));
    document.getElementById('vehicleRemoveFile')?.addEventListener('click',()=>{
      if(draft.file && !draft.file.persisted)try{native().discardDocumentFile(draft.file.path);}catch(_){}
      draft.file=null;captureDraft();renderServiceForm();
    });
    document.getElementById('vehicleRemoveService')?.addEventListener('click',()=>{
      confirmed('Eliminar actuación','Se eliminará esta visita del historial del vehículo.',()=>{
        if(native().deleteVehicleService(draft.id)){draft.saved=true;close();render();message('Actuación eliminada.');}
        else message('No se pudo eliminar la actuación.');
      });
    });
  }
  function openService(vehicleId=0,serviceId=0,repeat=null){
    const id=vehicleId||selectedId();
    if(!id){openVehicle();return;}
    const original=serviceId?services(id).find(s=>n(s.id)===n(serviceId)):null;
    if(serviceId&&!original){message('La actuación ya no existe.');return;}
    const copying=repeat&&!original ? repeat:null;
    const chosen=original||copying;
    showModal(original?'Editar mantenimiento':copying?'Repetir mantenimiento':'Nuevo mantenimiento','<p>Preparando operaciones…</p>');
    draft={
      id:original?n(original.id):0,vehicleId:id,
      date:original?original.date:dateNow(),
      mileage:original?(original.mileage==null?'':String(original.mileage)):String(getVehicle(id)?.mileage||''),
      cost:original&&original.costCents!=null?(Number(original.costCents)/100).toFixed(2):'',
      selection:new Set(Array.isArray(chosen?.operations)?chosen.operations:[]),
      file:original?.attachmentPath?{path:original.attachmentPath,name:original.attachmentName,mime:original.attachmentMime,persisted:true}:null,
      saved:false
    };
    renderServiceForm();
  }
  function addOperation(){
    const name=document.getElementById('vehicleNewOperation')?.value.replace(/\s+/g,' ').trim() || '';
    if(!name){message('Introduce una operación personalizada una sola vez.');return;}
    if(base.has(name.toLocaleLowerCase('es'))){message('Ya puedes marcar esta operación en el catálogo.');return;}
    captureDraft();
    const result=respond(native().saveVehicleOperation(JSON.stringify({label:name})));
    if(result.ok){draft.selection.add(result.label||name);renderServiceForm();message('Operación añadida al catálogo.');}
  }
  function saveService(event){
    event.preventDefault();captureDraft();
    if(!draft||!draft.selection.size){message('Marca al menos una operación realizada.');return;}
    if(!draft.date){message('Selecciona una fecha.');return;}
    const km=draft.mileage===''?null:Number(draft.mileage);
    const cost=draft.cost===''?null:Number(draft.cost);
    if(km!==null&&(!Number.isSafeInteger(km)||km<0||km>10000000)){message('Kilometraje no válido.');return;}
    if(cost!==null&&(!Number.isFinite(cost)||cost<0||cost>1000000)){message('Coste no válido.');return;}
    const input={
      id:draft.id,vehicleId:draft.vehicleId,date:draft.date,mileage:km,
      costCents:cost===null?null:Math.round(cost*100),
      operations:[...draft.selection],
      attachmentPath:draft.file?.path||'',attachmentName:draft.file?.name||'',attachmentMime:draft.file?.mime||''
    };
    const result=respond(native().saveVehicleService(JSON.stringify(input)));
    if(result.ok){draft.saved=true;close();render();message('Mantenimiento guardado.');}
  }
  function onFileChosen(result){
    if(!draft || !activeRequest || result.request!==activeRequest)return false;
    activeRequest='';
    if(result.error){message(result.error);return true;}
    if(result.file?.path){
      if(draft.file?.path && !draft.file.persisted && draft.file.path!==result.file.path){
        try{native().discardDocumentFile(draft.file.path);}catch(_){}
      }
      captureDraft();draft.file={...result.file,persisted:false};renderServiceForm();message('Factura adjuntada.');
    }
    return true;
  }

  function openDue(id,operation=''){
    const v=getVehicle(id);if(!v)return;
    const old=rules(id).find(x=>x.operation===operation);
    const options=[...new Set([...CATALOG.flatMap(g=>g.items),...custom().map(c=>c.label)])];
    const current=operation||options[0]||'';
    showModal(old?'Editar aviso':'Programar revisión',
      '<form class="vehicle-form" id="vehicleDueForm">'+
      '<label>Operación<select id="vehicleDueOperation" '+(old?'disabled':'')+'>'+
      (old&&!options.includes(operation)?'<option value="'+html(operation)+'">'+html(operation)+'</option>':'')+
      options.map(label=>'<option value="'+html(label)+'"'+(label===current?' selected':'')+'>'+html(label)+'</option>').join('')+
      '</select></label>'+
      '<label>Fecha prevista · opcional<input id="vehicleDueDate" type="date" value="'+html(old?.dueDate||'')+'"></label>'+
      '<label>Kilómetros previstos · opcional<input id="vehicleDueKm" type="number" min="0" max="10000000" step="1" placeholder="Sin límite de kilómetros" value="'+(old?.dueMileage==null?'':html(old.dueMileage))+'"></label>'+
      '<p class="vehicle-hint">Indica una fecha, un kilometraje o ambos. No se asignan intervalos genéricos.</p>'+
      '<button type="submit" class="primary-button">Guardar revisión</button>'+
      (old?'<button type="button" class="danger-button" id="vehicleDueDelete">Eliminar aviso</button>':'')+'</form>');
    document.getElementById('vehicleDueForm').addEventListener('submit',event=>{
      event.preventDefault();
      const date=document.getElementById('vehicleDueDate').value;
      const str=document.getElementById('vehicleDueKm').value,km=str===''?null:Number(str);
      if(!date&&km===null){message('Indica una fecha o kilómetros previstos.');return;}
      if(km!==null&&(!Number.isSafeInteger(km)||km<0||km>10000000)){message('Kilometraje no válido.');return;}
      const result=respond(native().saveVehicleDueRule(JSON.stringify({
        vehicleId:id,operation:old?.operation||document.getElementById('vehicleDueOperation').value,
        dueDate:date,dueMileage:km
      })));
      if(result.ok){close();render();message('Revisión programada.');}
    });
    document.getElementById('vehicleDueDelete')?.addEventListener('click',()=>{
      if(native().deleteVehicleDueRule(id,old.operation)){close();render();message('Aviso eliminado.');}
    });
  }

  function manageCatalog(){
    const customRows=custom();
    showModal('Mis operaciones','<div class="vehicle-catalog-manage"><p>Las operaciones que añadas se reutilizan en todos los coches. Ocultar o renombrar una no modifica las actuaciones anteriores.</p>'+
      (customRows.length?customRows.map(op=>
        '<div class="vehicle-catalog-item"><strong>'+html(op.label)+'</strong><button class="secondary-button" data-custom-edit="'+n(op.id)+'" type="button">Renombrar</button><button class="vehicle-link" data-custom-hide="'+n(op.id)+'" type="button">Ocultar</button></div>').join(''):'<p class="vehicle-hint">Todavía no hay operaciones personalizadas.</p>')+
      '<label>Operación nueva<input id="vehicleCatalogName" maxlength="90" placeholder="Nueva operación"></label>'+
      '<button class="primary-button" type="button" id="vehicleCatalogSave">Añadir al catálogo</button></div>');
    document.getElementById('vehicleCatalogSave').addEventListener('click',()=>{
      const label=document.getElementById('vehicleCatalogName').value.trim();
      if(base.has(label.toLocaleLowerCase('es'))){message('Esta operación ya está en el catálogo.');return;}
      const result=respond(native().saveVehicleOperation(JSON.stringify({label})));
      if(result.ok){manageCatalog();message('Operación guardada.');}
    });
    document.querySelectorAll('[data-custom-edit]').forEach(button=>button.addEventListener('click',()=>{
      const op=customRows.find(x=>n(x.id)===n(button.dataset.customEdit));
      if(!op)return;
      const label=window.prompt('Nuevo nombre de la operación',op.label);
      if(label===null)return;
      if(base.has(label.trim().toLocaleLowerCase('es'))){message('Esta operación ya está en el catálogo.');return;}
      const result=respond(native().saveVehicleOperation(JSON.stringify({id:op.id,label})));
      if(result.ok){manageCatalog();message('Operación renombrada.');}
    }));
    document.querySelectorAll('[data-custom-hide]').forEach(button=>button.addEventListener('click',()=>{
      const op=customRows.find(x=>n(x.id)===n(button.dataset.customHide));
      if(!op)return;
      confirmed('Ocultar operación','Ya no aparecerá entre las casillas, pero se conservará el historial.',()=>{
        if(native().hideVehicleOperation(op.id)){manageCatalog();message('Operación ocultada.');}
      });
    }));
  }
  window.NexoVehicles={
    render,openVehicle,openService,onFileChosen,
    onModalClose:stopDraft,
    catalog:CATALOG
  };
})();