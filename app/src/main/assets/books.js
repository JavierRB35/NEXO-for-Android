'use strict';

/* Biblioteca: dos listas, búsqueda incremental global y dictado a texto. */
window.NexoBooks = (() => {
  let tab = 'read', search = '', books = [], editor = null;
  const labels = {want:'Quiero leer',reading:'En lectura',finished:'Terminado',abandoned:'Abandonado'};
  const normal = v => String(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es-ES').trim();
  const today = () => {const d=new Date();return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');};
  const showDate = v => /^\d{4}-\d{2}-\d{2}$/.test(String(v||''))?String(v).split('-').reverse().join('/'):''; 

  function card(b) {
    const id=Number(b.id), percent=Math.max(0,Math.min(100,Number(b.progress)||0));
    const date=b.status==='finished'?showDate(b.finishDate):b.status==='abandoned'?showDate(b.abandonedDate):'';
    return `<article class="book-card">
      <button class="book-card-main" type="button" data-book-edit="${id}">
        <span class="book-card-top"><strong>${esc(b.title)}</strong><span class="book-status book-status--${escAttr(b.status)}">${esc(labels[b.status]||labels.want)}</span></span>
        <span class="book-author">${esc(b.author)}</span>
        ${b.status!=='want'?`<span class="book-progress-row"><span class="book-progress-track"><span style="width:${percent}%"></span></span><b>${percent}%</b></span>`:''}
        ${date?`<span class="book-date">${b.status==='finished'?'Finalizado':'Abandonado'}: ${date}</span>`:''}
        ${b.summary&&b.status!=='want'?`<span class="book-excerpt">${esc(b.summary)}</span>`:''}
      </button>
      <div class="book-card-actions">
        ${b.status==='want'?`<button type="button" class="mini-button primary" data-book-start="${id}">Empezar lectura</button>`:''}
        <button type="button" class="mini-button" data-book-edit="${id}">Editar</button>
        <button type="button" class="mini-button book-delete-button" data-book-delete="${id}" aria-label="Eliminar libro">${uiIcon('trash')}</button>
      </div>
    </article>`;
  }

  function refreshMatches() {
    const list=document.querySelector('#booksResults'), counter=document.querySelector('#booksSearchCount');
    if(!list)return;
    const q=normal(search);
    const rows=q?books.filter(b=>normal(b.title).includes(q)||normal(b.author).includes(q))
      :books.filter(b=>tab==='want'?b.status==='want':b.status!=='want');
    if(counter)counter.textContent=q
      ?`${rows.length} resultado${rows.length===1?'':'s'} · buscando en ambas listas`
      :`${rows.length} libro${rows.length===1?'':'s'}`;
    list.innerHTML=rows.length?`<div class="books-stack">${rows.map(card).join('')}</div>`
      :`<div class="empty-state">${q?'No hay libros que contengan ese texto en el título o el autor.':tab==='want'?'No hay libros pendientes. Añade el primero.':'Todavía no tienes libros leídos o empezados.'}</div>`;
    list.querySelectorAll('[data-book-edit]').forEach(b=>b.addEventListener('click',()=>openEditor(Number(b.dataset.bookEdit))));
    list.querySelectorAll('[data-book-start]').forEach(b=>b.addEventListener('click',()=>startBook(Number(b.dataset.bookStart))));
    list.querySelectorAll('[data-book-delete]').forEach(b=>b.addEventListener('click',()=>removeBook(Number(b.dataset.bookDelete))));
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
      <div class="books-tabs" role="group" aria-label="Listas de libros">
        <button class="books-tab ${tab==='read'?'active':''}" type="button" data-books-tab="read" aria-pressed="${tab==='read'}">Leídos y empezados</button>
        <button class="books-tab ${tab==='want'?'active':''}" type="button" data-books-tab="want" aria-pressed="${tab==='want'}">Quiero leer</button>
      </div>
      <div id="booksResults"></div>
    </div>`;
    host.querySelector('#booksSearchInput').addEventListener('input',e=>{search=e.target.value;refreshMatches();});
    host.querySelectorAll('[data-books-tab]').forEach(b=>b.addEventListener('click',()=>{
      tab=b.dataset.booksTab;
      host.querySelectorAll('[data-books-tab]').forEach(button=>{
        const active=button.dataset.booksTab===tab;
        button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));
      });
      refreshMatches();
    }));
    refreshMatches();
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
    document.querySelector('#bookFinishRow')?.classList.toggle('hidden',status!=='finished');
    document.querySelector('#bookAbandonedRow')?.classList.toggle('hidden',status!=='abandoned');
    if(slider){
      if(status==='finished')slider.value='100';
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
    const status=source.status||initialStatus||(tab==='want'?'want':'reading');
    const want=status==='want';
    openModal(id?'Editar libro':'Nuevo libro',`<div class="modal-form book-editor">
      <label>Título<input id="bookTitle" type="text" maxlength="250" value="${escAttr(source.title||'')}" placeholder="Título del libro" required></label>
      <label>Autor<input id="bookAuthor" type="text" maxlength="180" value="${escAttr(source.author||'')}" placeholder="Nombre del autor" required></label>
      ${want?'':`<label>Estado de lectura<select id="bookStatus">
        <option value="reading" ${status==='reading'?'selected':''}>En lectura</option>
        <option value="finished" ${status==='finished'?'selected':''}>Terminado</option>
        <option value="abandoned" ${status==='abandoned'?'selected':''}>Abandonado</option>
      </select></label>
      <div class="book-slider-heading"><label for="bookProgress">Progreso de lectura</label><strong id="bookProgressValue">${Number(source.progress)||0}%</strong></div>
      <input id="bookProgress" type="range" min="0" max="100" step="1" value="${Number(source.progress)||0}">
      <div class="book-slider-ends"><small>0 % · Sin empezar</small><small>100 % · Terminado</small></div>
      <label id="bookFinishRow" class="hidden">Fecha de finalización<input id="bookFinishDate" type="date" value="${escAttr(source.finishDate||'')}"></label>
      <label id="bookAbandonedRow" class="hidden">Fecha de abandono<input id="bookAbandonedDate" type="date" value="${escAttr(source.abandonedDate||'')}"></label>
      <label>Mi resumen personal <span class="optional">(opcional)</span><textarea id="bookSummary" rows="5" maxlength="20000" placeholder="Ideas, conclusiones y cosas que quieras recordar…">${esc(source.summary||'')}</textarea></label>
      <button id="bookDictate" class="secondary-button book-dictate" type="button">${uiIcon('mic')} Dictar resumen</button>
      <small class="muted">El dictado se convierte a texto editable; no se almacena audio.</small>`}
      <p class="book-form-error hidden" id="bookError" role="alert"></p>
    </div>
    <div class="modal-actions">
      <button class="secondary-button" id="bookCancel" type="button">Cancelar</button>
      <button class="primary-button" id="bookSave" type="button">Guardar libro</button>
    </div>`);
    editor={id:Number(id)||0,baseline:''};
    if(!want){
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
    }
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
    editor=null;closeModal();render();toast('Libro guardado correctamente.');
  }

  function startBook(id){
    const b=books.find(x=>Number(x.id)===id);
    if(!b||b.status!=='want')return;
    const r=parseNative(NexoNative.saveBook(JSON.stringify({
      id,title:b.title,author:b.author,status:'reading',progress:0,finishDate:'',abandonedDate:'',summary:''
    })),{ok:false});
    if(!r.ok){toast(r.error||'No se pudo empezar la lectura.');return;}
    tab='read';search='';render();toast('Libro movido a Leídos y empezados.');
  }

  function removeBook(id){
    const b=books.find(x=>Number(x.id)===id);if(!b)return;
    confirmGeneric('Eliminar libro','¿Eliminar “'+b.title+'” de tu biblioteca?',()=>{
      const ok=NexoNative.deleteBook(id);toast(ok?'Libro eliminado.':'No se pudo eliminar el libro.');
      if(ok)render();
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
