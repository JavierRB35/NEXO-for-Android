'use strict';

/* Biblioteca: dos listas, búsqueda incremental global y dictado a texto. */
window.NexoBooks = (() => {
  let filter = 'all', search = '', books = [], editor = null;
  const labels = {want:'Quiero leer',reading:'En lectura',finished:'Terminado',abandoned:'Abandonado'};
  const icons = {want:'book-want',reading:'book-reading',finished:'book-finished',abandoned:'book-abandoned'};
  const normal = v => String(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es-ES').trim();
  const today = () => {const d=new Date();return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');};
  const showDate = v => /^\d{4}-\d{2}-\d{2}$/.test(String(v||''))?String(v).split('-').reverse().join('/'):''; 

  function card(b) {
    const id=Number(b.id), percent=Math.max(0,Math.min(100,Number(b.progress)||0));
    const status=Object.hasOwn(labels,b.status)?b.status:'want';
    const name=labels[status];
    return `<article class="book-card">
      <button class="book-card-main" type="button" data-book-open="${id}" aria-label="Ver ficha de ${escAttr(b.title)}, ${escAttr(b.author)}. ${escAttr(name)}">
        <span class="book-card-symbol book-card-symbol--${status}" aria-hidden="true">${uiIcon(icons[status])}</span>
        <span class="book-card-copy">
          <strong class="book-card-title">${esc(b.title)}</strong>
          <span class="book-card-subline"><span class="book-author">${esc(b.author)}</span>${status!=='want'?`<span class="book-card-percent">${percent}%</span>`:''}</span>
        </span>
        <span class="book-card-chevron" aria-hidden="true">${uiIcon('right')}</span>
      </button>
    </article>`;
  }

  function refreshMatches() {
    const list=document.querySelector('#booksResults'), counter=document.querySelector('#booksSearchCount');
    if(!list)return;
    const q=normal(search);
    const rows=books.filter(b=>(filter==='all'||b.status===filter)&&
      (!q||normal(b.title).includes(q)||normal(b.author).includes(q)));
    if(counter)counter.textContent=q
      ?`${rows.length} resultado${rows.length===1?'':'s'} · ${filter==='all'?'todos los estados':labels[filter]}`
      :`${rows.length} libro${rows.length===1?'':'s'}`;
    list.innerHTML=rows.length?`<div class="books-stack">${rows.map(card).join('')}</div>`
      :`<div class="empty-state">${q?'No hay libros con ese título o autor en el filtro seleccionado.':'No hay libros en este estado. Puedes añadir uno con el botón +.'}</div>`;
    list.querySelectorAll('[data-book-open]').forEach(b=>b.addEventListener('click',()=>openDetails(Number(b.dataset.bookOpen))));
  }

  function render() {
    try{books=parseNative(NexoNative.getBooks(),[]);}catch(_){books=[];toast('No se pudo consultar la biblioteca.');}
    const host=document.querySelector('#panelContent');if(!host)return;
    host.innerHTML=`<div class="books-library">
      <label class="books-search" for="booksSearchInput">
        <span class="books-search-title">${uiIcon('search')} Buscar libro</span>
        <input id="booksSearchInput" type="search" autocomplete="off" spellcheck="false" placeholder="Título o autor · búsqueda instantánea" value="${escAttr(search)}">
        <small id="booksSearchCount" aria-live="polite"></small>
      </label>
      <div class="books-status-filters" role="group" aria-label="Filtrar libros por estado">
        ${[['all','Todos'],...Object.entries(labels)].map(([key,text])=>`<button class="books-status-filter ${filter===key?'active':''}" type="button" data-books-filter="${key}" aria-pressed="${filter===key}">${key==='all'?'':uiIcon(icons[key])}${esc(text)}</button>`).join('')}
      </div>
      <div id="booksResults"></div>
    </div>`;
    host.querySelector('#booksSearchInput').addEventListener('input',e=>{search=e.target.value;refreshMatches();});
    host.querySelectorAll('[data-books-filter]').forEach(b=>b.addEventListener('click',()=>{
      filter=b.dataset.booksFilter;
      host.querySelectorAll('[data-books-filter]').forEach(button=>{
        const active=button.dataset.booksFilter===filter;
        button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));
      });
      refreshMatches();
    }));
    refreshMatches();
  }

  function openDetails(id){
    const b=Number(id)?parseNative(NexoNative.getBook(Number(id)),{}):{};
    if(!b.id){toast('Libro no encontrado.');render();return;}
    const status=Object.hasOwn(labels,b.status)?b.status:'want';
    const progress=Math.max(0,Math.min(100,Number(b.progress)||0));
    const finished=status==='finished'?showDate(b.finishDate):'';
    const abandoned=status==='abandoned'?showDate(b.abandonedDate):'';
    openModal('Ficha del libro',`<article class="book-detail">
      <div class="book-detail-head">
        <span class="book-card-symbol book-card-symbol--${status}" aria-hidden="true">${uiIcon(icons[status])}</span>
        <div class="book-detail-heading">
          <h3>${esc(b.title)}</h3>
          <p>${esc(b.author)}</p>
          <span class="book-status book-status--${status}">${esc(labels[status])}</span>
        </div>
      </div>
      ${status!=='want'?`<section class="book-detail-section"><strong>Progreso de lectura</strong>
        <div class="book-progress-row"><span class="book-progress-track"><span style="width:${progress}%"></span></span><b>${progress}%</b></div>
      </section>`:''}
      ${finished?`<p class="book-detail-date"><strong>Finalizado:</strong> ${finished}</p>`:''}
      ${abandoned?`<p class="book-detail-date"><strong>Abandonado:</strong> ${abandoned}</p>`:''}
      ${status!=='want'?`<section class="book-detail-section"><strong>Mi resumen personal</strong>
        <div class="book-detail-summary">${b.summary?esc(b.summary):'<span class="muted">Todavía no has añadido un resumen.</span>'}</div>
      </section>`:''}
    </article>
    <div class="modal-actions wrap-actions book-detail-actions">
      <button type="button" id="bookDetailDelete" class="danger-button">${uiIcon('trash')} Eliminar</button>
      ${status==='want'?`<button type="button" id="bookDetailStart" class="secondary-button">${uiIcon('book-reading')} Empezar lectura</button>`:''}
      <button type="button" id="bookDetailEdit" class="primary-button">${uiIcon('edit')} Editar</button>
    </div>`);
    document.querySelector('#bookDetailEdit').addEventListener('click',()=>openEditor(Number(b.id)));
    document.querySelector('#bookDetailDelete').addEventListener('click',()=>removeBook(Number(b.id)));
    document.querySelector('#bookDetailStart')?.addEventListener('click',()=>startBook(Number(b.id)));
  }

  function readFields(){
    if(!editor)return null;
    return {
      id:editor.id,
      title:document.querySelector('#bookTitle')?.value.trim()||'',
      author:document.querySelector('#bookAuthor')?.value.trim()||'',
      status:document.querySelector('#bookStatus')?.value||'want',
      progress:Number(document.querySelector('#bookProgress')?.value||0),
      finishDate:document.querySelector('#bookFinishDate')?.value||'',
      abandonedDate:document.querySelector('#bookAbandonedDate')?.value||'',
      summary:document.querySelector('#bookSummary')?.value.trim()||''
    };
  }

  function updateForm(){
    const select=document.querySelector('#bookStatus');if(!select)return;
    const status=select.value,slider=document.querySelector('#bookProgress');
    const want=status==='want';
    document.querySelector('#bookReadingFields')?.classList.toggle('hidden',want);
    document.querySelector('#bookFinishRow')?.classList.toggle('hidden',status!=='finished');
    document.querySelector('#bookAbandonedRow')?.classList.toggle('hidden',status!=='abandoned');
    if(slider){
      if(want)slider.value='0';
      else if(status==='finished')slider.value='100';
      else if(Number(slider.value)>=100)slider.value='99';
      const label=document.querySelector('#bookProgressValue');
      if(label)label.textContent=slider.value+'%';
    }
    const finished=document.querySelector('#bookFinishDate'),abandoned=document.querySelector('#bookAbandonedDate');
    if(status==='finished'&&finished&&!finished.value)finished.value=today();
    if(status==='abandoned'&&abandoned&&!abandoned.value)abandoned.value=today();
  }

  function openEditor(id=0,initialStatus=''){
    const source=Number(id)?parseNative(NexoNative.getBook(Number(id)),{}):{};
    if(id&&!source.id){toast('Libro no encontrado.');render();return;}
    const status=source.status||initialStatus||(filter==='all'?'want':filter);
    openModal(id?'Editar libro':'Nuevo libro',`<div class="modal-form book-editor">
      <label>Título<input id="bookTitle" type="text" maxlength="250" value="${escAttr(source.title||'')}" placeholder="Título del libro" required></label>
      <label>Autor<input id="bookAuthor" type="text" maxlength="180" value="${escAttr(source.author||'')}" placeholder="Nombre del autor" required></label>
      <label>Estado de lectura<select id="bookStatus">
        ${Object.entries(labels).map(([key,label])=>`<option value="${key}" ${status===key?'selected':''}>${esc(label)}</option>`).join('')}
      </select></label>
      <div id="bookReadingFields">
        <div class="book-slider-heading"><label for="bookProgress">Progreso de lectura</label><strong id="bookProgressValue">${Number(source.progress)||0}%</strong></div>
        <input id="bookProgress" type="range" min="0" max="100" step="1" value="${Number(source.progress)||0}">
        <div class="book-slider-ends"><small>0 % · Sin empezar</small><small>100 % · Terminado</small></div>
        <label id="bookFinishRow" class="hidden">Fecha de finalización<input id="bookFinishDate" type="date" value="${escAttr(source.finishDate||'')}"></label>
        <label id="bookAbandonedRow" class="hidden">Fecha de abandono<input id="bookAbandonedDate" type="date" value="${escAttr(source.abandonedDate||'')}"></label>
        <label>Mi resumen personal <span class="optional">(opcional)</span><textarea id="bookSummary" rows="5" maxlength="20000" placeholder="Ideas, conclusiones y cosas que quieras recordar…">${esc(source.summary||'')}</textarea></label>
        <button id="bookDictate" class="secondary-button book-dictate" type="button">${uiIcon('mic')} Dictar resumen</button>
        <small class="muted">El dictado se convierte a texto editable; no se almacena audio.</small>
      </div>
      <p class="book-form-error hidden" id="bookError" role="alert"></p>
    </div>
    <div class="modal-actions">
      <button class="secondary-button" id="bookCancel" type="button">Cancelar</button>
      <button class="primary-button" id="bookSave" type="button">Guardar libro</button>
    </div>`);
    editor={id:Number(id)||0,baseline:''};
    document.querySelector('#bookStatus').addEventListener('change',updateForm);
    document.querySelector('#bookProgress').addEventListener('input',e=>{
      if(Number(e.target.value)===100)document.querySelector('#bookStatus').value='finished';
      else if(document.querySelector('#bookStatus').value==='finished')document.querySelector('#bookStatus').value='reading';
      updateForm();
    });
    document.querySelector('#bookDictate').addEventListener('click',()=>{
      startManagedDictation('book-summary',{
        base:document.querySelector('#bookSummary')?.value||'',
        title:'DICTAR RESUMEN DEL LIBRO',
        hint:'Explica lo que te ha parecido el libro y pulsa TERMINAR.'
      });
    });
    updateForm();
    editor.baseline=JSON.stringify(readFields());
    document.querySelector('#bookCancel').addEventListener('click',()=>closeModal());
    document.querySelector('#bookSave').addEventListener('click',saveBook);
    requestAnimationFrame(()=>document.querySelector('#bookTitle')?.focus());
  }

  function saveBook(){
    const payload=readFields();if(!payload)return;
    const error=document.querySelector('#bookError');error.classList.add('hidden');
    if(!payload.title||!payload.author){
      error.textContent='Indica el título y el autor.';error.classList.remove('hidden');
      (!payload.title?document.querySelector('#bookTitle'):document.querySelector('#bookAuthor')).focus();return;
    }
    const result=parseNative(NexoNative.saveBook(JSON.stringify(payload)),{ok:false});
    if(!result.ok){
      error.textContent=result.error||'No se pudo guardar el libro.';error.classList.remove('hidden');return;
    }
    editor=null;closeModal();
    if(filter!=='all'&&filter!==payload.status)filter=payload.status;
    render();toast('Libro guardado correctamente.');
  }

  function startBook(id){
    const b=books.find(x=>Number(x.id)===id);
    if(!b||b.status!=='want')return;
    const r=parseNative(NexoNative.saveBook(JSON.stringify({
      id,title:b.title,author:b.author,status:'reading',progress:0,finishDate:'',abandonedDate:'',summary:''
    })),{ok:false});
    if(!r.ok){toast(r.error||'No se pudo empezar la lectura.');return;}
    closeModal();filter='reading';search='';render();toast('Libro movido a En lectura.');
  }

  function removeBook(id){
    const b=books.find(x=>Number(x.id)===id);if(!b)return;
    confirmGeneric('Eliminar libro','¿Eliminar “'+b.title+'” de tu biblioteca?',()=>{
      const ok=NexoNative.deleteBook(id);toast(ok?'Libro eliminado.':'No se pudo eliminar el libro.');
      if(ok){closeModal();render();}
    });
  }

  function preventClose(){
    if(!editor)return false;
    if(editor.baseline!==JSON.stringify(readFields())){
      confirmGeneric('Descartar cambios','Hay cambios sin guardar en este libro. ¿Quieres descartarlos?',()=>{
        editor=null;closeModal(true);
      },'Descartar');
      return true;
    }
    editor=null;return false;
  }

  function applyVoiceText(text){
    const field=document.querySelector('#bookSummary');
    if(!editor||!field){toast('El editor del libro ya no está abierto.');return;}
    if(String(text||'').trim()){
      field.value=String(text).trim();
      toast('Resumen transcrito. Revísalo y guarda el libro.');
    }
  }

  return {render,openEditor,preventClose,applyVoiceText};
})();
