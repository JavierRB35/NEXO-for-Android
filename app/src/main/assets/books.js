'use strict';

/* Biblioteca de Nexo: estados, fechas reales, favoritos y valoración independientes. */
window.NexoBooks = (() => {
  let filter='all', categoryFilter='all', search='', books=[], editor=null;
  const labels={want:'Quiero leer',reading:'En lectura',finished:'Terminado',abandoned:'Abandonado'};
  const icons={want:'book-want',reading:'book-reading',finished:'book-finished',abandoned:'book-abandoned'};
  // Catálogo exclusivo de este módulo; se admiten categorías personalizadas.
  const categoryPresets=["Novela","Ciencia ficción","Fantasía","Misterio y suspense","Historia","Biografía y memorias","Ensayo","Divulgación","Desarrollo personal","Empresa y economía","Tecnología","Poesía","Otros"];
  const categoryOf=b=>String(b?.category||'').trim();
  const normal=v=>String(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('es-ES').trim();
  const today=()=>{const d=new Date();return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');};
  const showDate=v=>/^\d{4}-\d{2}-\d{2}$/.test(String(v||''))?String(v).split('-').reverse().join('/'):'';
  const validDate=v=>/^\d{4}-\d{2}-\d{2}$/.test(String(v||''))?String(v):'';
  const number=v=>Number(v)||0;
  const ratingOf=b=>Math.max(0,Math.min(5,Math.trunc(number(b.rating))));

  function bookDate(b){
    if(b.status==='finished')return validDate(b.finishDate);
    if(b.status==='abandoned')return validDate(b.abandonedDate);
    if(b.status==='reading')return validDate(b.startDate);
    return '';
  }
  function sortBooks(rows){
    return rows.slice().sort((a,b)=>{
      // En cualquier filtro, fechas reales primero y más recientes arriba.
      // No reemplazar finishDate por updatedAt al editar un resumen.
      const da=bookDate(a),db=bookDate(b);
      if(da!==db){if(!da)return 1;if(!db)return -1;return da>db?-1:1;}
      const ua=number(a.updatedAt),ub=number(b.updatedAt);
      if(ua!==ub)return ub-ua;
      return number(b.id)-number(a.id);
    });
  }
  function readBooks(){
    try{books=parseNative(NexoNative.getBooks(),[]);if(!Array.isArray(books))books=[];}
    catch(_){books=[];toast('No se pudo consultar la biblioteca.');}
  }
  function searchedBooks(){
    const q=normal(search);
    return q?books.filter(b=>normal(b.title).includes(q)||normal(b.author).includes(q)):books;
  }
  function countFor(key,rows){
    if(key==='all')return rows.length;
    if(key==='favorites')return rows.filter(b=>!!b.favorite).length;
    return rows.filter(b=>b.status===key).length;
  }
  function matchesFilter(b){
    return (filter==='all'||filter==='favorites'&&!!b.favorite||b.status===filter)&&
      (categoryFilter==='all'||categoryOf(b)===categoryFilter);
  }
  function renderFilterCounts(){
    const rows=searchedBooks();
    document.querySelectorAll('[data-book-count]').forEach(e=>{
      e.textContent=String(countFor(e.dataset.bookCount,rows));
    });
  }
  function statsText(){
    const year=today().slice(0,4);
    const count=s=>books.filter(b=>b.status===s).length;
    const yearFinished=books.filter(b=>b.status==='finished'&&validDate(b.finishDate).startsWith(year)).length;
    const favorites=books.filter(b=>!!b.favorite).length;
    return `<div class="books-stats-grid">
      <span><b>${yearFinished}</b><small>Terminados en ${year}</small></span>
      <span><b>${count('reading')}</b><small>En lectura</small></span>
      <span><b>${count('want')}</b><small>Quiero leer</small></span>
      <span><b>${count('finished')}</b><small>Terminados en total</small></span>
      <span><b>${count('abandoned')}</b><small>Abandonados</small></span>
      <span><b>${favorites}</b><small>Favoritos</small></span>
    </div>`;
  }
  function refreshStats(){
    const container=document.querySelector('#booksStatsContent');
    if(container)container.innerHTML=statsText();
    const summary=document.querySelector('#booksStatsTotal');
    if(summary)summary.textContent=`${books.length} libro${books.length===1?'':'s'} en total`;
  }

  function card(b) {
    const id=number(b.id),percent=Math.max(0,Math.min(100,number(b.progress)));
    const status=Object.hasOwn(labels,b.status)?b.status:'want';
    const date=showDate(bookDate(b));
    const rating=ratingOf(b);
    const description=[labels[status],categoryOf(b)||'Sin categoría',date,b.favorite?'Favorito':'',rating?`${rating} de 5 estrellas`:'Sin valorar'].filter(Boolean).join('. ');
    return `<article class="book-card">
      <button class="book-card-main" type="button" data-book-open="${id}" aria-label="Ver ficha de ${escAttr(b.title)}, ${escAttr(b.author)}. ${escAttr(description)}">
        <span class="book-card-symbol book-card-symbol--${status}" aria-hidden="true">${uiIcon(icons[status])}</span>
        <span class="book-card-copy">
          <strong class="book-card-title">${esc(b.title)}</strong>
          <span class="book-card-subline"><span class="book-author">${esc(b.author)}</span>${date?`<span class="book-card-date">${date}</span>`:''}${status!=='want'?`<span class="book-card-percent">${percent}%</span>`:''}</span>
          ${categoryOf(b)?`<span class="book-category-chip">${esc(categoryOf(b))}</span>`:'' }
          <span class="book-card-rating" role="img" aria-label="${rating?`Valoración: ${rating} de 5 estrellas`:'Sin valorar'}">${[1,2,3,4,5].map(n=>`<span class="book-card-star ${n<=rating?'is-set':''}" aria-hidden="true">${uiIcon('star')}</span>`).join('')}</span>
        </span>
        <span class="book-card-trailing" aria-hidden="true">${b.favorite?`<span class="book-card-heart">${uiIcon('heart')}</span>`:''}<span class="book-card-chevron">${uiIcon('right')}</span></span>
      </button>
    </article>`;
  }

  function refreshCategoryFilter(){
    const select=document.querySelector('#booksCategoryFilter');
    if(!select)return;
    const names=[...new Set(books.map(categoryOf))].sort((a,b)=>a.localeCompare(b,'es'));
    if(categoryFilter!=='all'&&!names.includes(categoryFilter))categoryFilter='all';
    select.innerHTML='<option value="all">Todas las categorías ('+books.length+')</option>'+
      names.map(name=>'<option value="'+escAttr(name)+'">'+
        esc(name||'Sin categoría')+' ('+books.filter(b=>categoryOf(b)===name).length+')</option>').join('');
    select.value=categoryFilter;
  }
  function categoryOptions(selected){
    const names=[...new Set([...categoryPresets,...books.map(categoryOf).filter(Boolean),selected].filter(Boolean))];
    return '<option value="">Sin categoría</option>'+
      names.map(name=>'<option value="'+escAttr(name)+'" '+(name===selected?'selected':'')+'>'+esc(name)+'</option>').join('')+
      '<option value="__custom">+ Crear otra categoría…</option>';
  }
  function updateCategoryCustom(){
    const custom=document.querySelector('#bookCategorySelect')?.value==='__custom';
    document.querySelector('#bookCategoryCustomRow')?.classList.toggle('hidden',!custom);
    if(custom)document.querySelector('#bookCategoryCustom')?.focus();
  }

  function refreshMatches() {
    const list=document.querySelector('#booksResults'),counter=document.querySelector('#booksSearchCount');
    if(!list)return;
    const q=normal(search),rows=sortBooks(searchedBooks().filter(matchesFilter));
    if(counter)counter.textContent=q?`${rows.length} resultado${rows.length===1?'':'s'}`:`${rows.length} libro${rows.length===1?'':'s'}`;
    list.innerHTML=rows.length?`<div class="books-stack">${rows.map(card).join('')}</div>`
      :`<div class="empty-state">${q?'No hay libros con ese título o autor en el filtro seleccionado.':'No hay libros en este filtro.'}</div>`;
    list.querySelectorAll('[data-book-open]').forEach(b=>b.addEventListener('click',()=>openDetails(number(b.dataset.bookOpen))));
    renderFilterCounts();
    refreshCategoryFilter();
  }

  function updateFilterButtons(){
    document.querySelectorAll('[data-books-filter]').forEach(button=>{
      const active=button.dataset.booksFilter===filter;
      button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));
    });
  }
  function refreshLibrary(){
    readBooks();
    updateFilterButtons();
    refreshStats();
    refreshMatches();
  }

  function render() {
    readBooks();
    const host=document.querySelector('#panelContent');if(!host)return;
    host.innerHTML=`<div class="books-library">
      <label class="books-search" for="booksSearchInput">
        <span class="books-search-title">${uiIcon('search')} Buscar libro</span>
        <input id="booksSearchInput" type="search" autocomplete="off" spellcheck="false" placeholder="Título o autor · búsqueda instantánea" value="${escAttr(search)}">
        <small id="booksSearchCount" aria-live="polite"></small>
      </label>
      <div class="books-status-filters" role="group" aria-label="Filtrar libros por estado">
        ${[['all','Todos'],...Object.entries(labels),['favorites','Favoritos']].map(([key,text])=>`<button class="books-status-filter ${filter===key?'active':''}" type="button" data-books-filter="${key}" aria-pressed="${filter===key}">${key==='all'?'':uiIcon(key==='favorites'?'heart':icons[key])}${esc(text)} <span class="books-filter-count" data-book-count="${key}">0</span></button>`).join('')}
      </div>
      <label class="book-category-filter" for="booksCategoryFilter"><span>Categoría</span><select id="booksCategoryFilter" aria-label="Filtrar por categoría"></select></label>
      <details class="books-stats" id="booksStats"><summary>${uiIcon('chart')} Estadísticas <span id="booksStatsTotal"></span></summary><div id="booksStatsContent"></div></details>
      <div id="booksResults"></div>
    </div>`;
    host.querySelector('#booksSearchInput').addEventListener('input',e=>{search=e.target.value;refreshMatches();});
    host.querySelector('#booksCategoryFilter').addEventListener('change',e=>{categoryFilter=e.target.value;refreshMatches();});
    host.querySelectorAll('[data-books-filter]').forEach(b=>b.addEventListener('click',()=>{
      filter=b.dataset.booksFilter;updateFilterButtons();refreshMatches();
    }));
    refreshStats();refreshMatches();
  }

  function paintRating(rating,selector,captionSelector){
    document.querySelectorAll(selector).forEach(button=>{
      const target=number(button.dataset.rating);
      button.classList.toggle('is-set',target<=rating);
      button.setAttribute('aria-pressed',String(target===rating));
    });
    const caption=document.querySelector(captionSelector);
    if(caption)caption.textContent=rating?`${rating} de 5 estrellas`:'Sin valorar';
  }
  function persistDetailChange(id,fields){
    const b=parseNative(NexoNative.getBook(id),{});
    if(!b.id)return toast('Libro no encontrado.');
    const updated={...b,...fields};
    const result=parseNative(NexoNative.saveBook(JSON.stringify(updated)),{ok:false});
    if(!result.ok){toast(result.error||'No se pudo actualizar el libro.');return;}
    const newBook=parseNative(NexoNative.getBook(id),updated);
    const favorite=document.querySelector('#bookDetailFavorite');
    if(favorite){
      favorite.classList.toggle('active',!!newBook.favorite);
      favorite.setAttribute('aria-pressed',String(!!newBook.favorite));
      favorite.setAttribute('aria-label',newBook.favorite?'Quitar de favoritos':'Añadir a favoritos');
      favorite.title=newBook.favorite?'Quitar de favoritos':'Añadir a favoritos';
    }
    paintRating(ratingOf(newBook),'[data-book-detail-rating]','#bookDetailRatingCaption');
    refreshLibrary();
  }
  function openDetails(id){
    const b=number(id)?parseNative(NexoNative.getBook(number(id)),{}):{};
    if(!b.id){toast('Libro no encontrado.');refreshLibrary();return;}
    const status=Object.hasOwn(labels,b.status)?b.status:'want';
    const progress=Math.max(0,Math.min(100,number(b.progress)));
    const finish=status==='finished'?showDate(b.finishDate):'';
    const abandoned=status==='abandoned'?showDate(b.abandonedDate):'';
    const start=showDate(b.startDate);
    const rating=ratingOf(b);
    openModal('Ficha del libro',`<article class="book-detail">
      <div class="book-detail-quick-actions record-card-actions" role="group" aria-label="Acciones del libro">
        <button id="bookDetailEdit" class="icon-action" type="button" aria-label="Editar libro" title="Editar libro">${uiIcon('edit')}</button>
        <button id="bookDetailShare" class="icon-action" type="button" aria-label="Compartir libro" title="Compartir libro">${uiIcon('share')}</button>
        <button id="bookDetailDelete" class="icon-action icon-action--danger" type="button" aria-label="Eliminar libro" title="Eliminar libro">${uiIcon('trash')}</button>
      </div>
      <div class="book-detail-head">
        <span class="book-card-symbol book-card-symbol--${status}" aria-hidden="true">${uiIcon(icons[status])}</span>
        <div class="book-detail-heading"><h3>${esc(b.title)}</h3><p>${esc(b.author)}</p>
          <span class="book-status book-status--${status}">${esc(labels[status])}</span>
        </div>
        <button id="bookDetailFavorite" type="button" class="book-favorite-toggle ${b.favorite?'active':''}" aria-pressed="${!!b.favorite}" aria-label="${b.favorite?'Quitar de favoritos':'Añadir a favoritos'}" title="${b.favorite?'Quitar de favoritos':'Añadir a favoritos'}">${uiIcon('heart')}</button>
      </div>
      <p class="book-detail-date"><strong>Categoría:</strong> ${esc(categoryOf(b)||'Sin categoría')}</p>
      <section class="book-detail-section"><strong>Argumento / sinopsis</strong>
        <div class="book-detail-summary">${b.synopsis?esc(b.synopsis):'<span class="muted">Todavía no has añadido un argumento.</span>'}</div>
      </section>
      ${status==='want'?`<div class="book-detail-start-row"><button type="button" id="bookDetailStart" class="secondary-button">${uiIcon('book-reading')} Empezar lectura</button></div>`:''}
      <div class="book-rating-section book-rating-section--detail"><strong>Mi valoración</strong>
        <div class="book-rating-stars" role="group" aria-label="Valorar este libro de 1 a 5 estrellas">
          ${[1,2,3,4,5].map(n=>`<button type="button" data-book-detail-rating data-rating="${n}" class="book-rating-star ${n<=rating?'is-set':''}" aria-pressed="${n===rating}" aria-label="${n} ${n===1?'estrella':'estrellas'}" title="${n} ${n===1?'estrella':'estrellas'}">${uiIcon('star')}</button>`).join('')}
        </div>
        <small id="bookDetailRatingCaption">${rating?`${rating} de 5 estrellas`:'Sin valorar'}</small>
      </div>
      ${start?`<p class="book-detail-date"><strong>Inicio de lectura:</strong> ${start}</p>`:''}
      ${status!=='want'?`<section class="book-detail-section"><strong>Progreso de lectura</strong>
        <div class="book-progress-row"><span class="book-progress-track"><span style="width:${progress}%"></span></span><b>${progress}%</b></div>
      </section>`:''}
      ${finish?`<p class="book-detail-date"><strong>Finalizado:</strong> ${finish}</p>`:''}
      ${abandoned?`<p class="book-detail-date"><strong>Abandonado:</strong> ${abandoned}</p>`:''}
      ${status!=='want'?`<section class="book-detail-section"><strong>Mi resumen personal</strong>
        <div class="book-detail-summary">${b.summary?esc(b.summary):'<span class="muted">Todavía no has añadido un resumen.</span>'}</div>
      </section>`:''}
    </article>`);
    document.querySelector('#bookDetailFavorite').addEventListener('click',()=>{
      const current=parseNative(NexoNative.getBook(number(b.id)),{});
      if(current.id)persistDetailChange(number(b.id),{favorite:!current.favorite});
    });
    document.querySelectorAll('[data-book-detail-rating]').forEach(button=>button.addEventListener('click',()=>{
      const current=parseNative(NexoNative.getBook(number(b.id)),{});
      const clicked=number(button.dataset.rating);
      if(current.id)persistDetailChange(number(b.id),{rating:ratingOf(current)===clicked?0:clicked});
    }));
    document.querySelector('#bookDetailEdit').addEventListener('click',()=>openEditor(number(b.id)));
    document.querySelector('#bookDetailShare').addEventListener('click',()=>{
      const live=parseNative(NexoNative.getBook(number(b.id)),{});
      if(!live.id){toast('Libro no encontrado.');return;}
      const parts=[live.title,live.author?'Autor: '+live.author:'',
        categoryOf(live)?'Categoría: '+categoryOf(live):'',
        live.synopsis?'Argumento / sinopsis:\n'+live.synopsis:'',
        labels[live.status]?'Estado: '+labels[live.status]:'',
        live.startDate?'Inicio: '+showDate(live.startDate):'',
        live.finishDate?'Finalización: '+showDate(live.finishDate):'',
        live.abandonedDate?'Abandono: '+showDate(live.abandonedDate):'',
        ratingOf(live)?'Valoración: '+ratingOf(live)+'/5':'',
        live.summary?'\n'+live.summary:''].filter(Boolean);
      const normalShare=()=>NexoNative.sharePlainText(live.title,parts.join('\n'));
      if(typeof openConnectShareChoice==='function')
        openConnectShareChoice('books',Number(live.id),normalShare);
      else normalShare();
    });
    document.querySelector('#bookDetailDelete').addEventListener('click',()=>removeBook(number(b.id)));
    document.querySelector('#bookDetailStart')?.addEventListener('click',()=>startBook(number(b.id)));
  }

  function readFields(){
    if(!editor)return null;
    return {
      id:editor.id,
      title:document.querySelector('#bookTitle')?.value.trim()||'',
      author:document.querySelector('#bookAuthor')?.value.trim()||'',
      category:document.querySelector('#bookCategorySelect')?.value==='__custom'
        ?(document.querySelector('#bookCategoryCustom')?.value.trim()||'')
        :(document.querySelector('#bookCategorySelect')?.value||''),
      synopsis:document.querySelector('#bookSynopsis')?.value.trim()||'',
      status:document.querySelector('#bookStatus')?.value||'want',
      progress:number(document.querySelector('#bookProgress')?.value),
      startDate:document.querySelector('#bookStartDate')?.value||'',
      finishDate:document.querySelector('#bookFinishDate')?.value||'',
      abandonedDate:document.querySelector('#bookAbandonedDate')?.value||'',
      summary:document.querySelector('#bookSummary')?.value.trim()||'',
      favorite:editor.favorite,
      rating:editor.rating
    };
  }

  function updateForm(fillDates=false){
    const select=document.querySelector('#bookStatus');if(!select)return;
    const status=select.value,slider=document.querySelector('#bookProgress');
    const want=status==='want';
    document.querySelector('#bookReadingFields')?.classList.toggle('hidden',want);
    document.querySelector('#bookFinishRow')?.classList.toggle('hidden',status!=='finished');
    document.querySelector('#bookAbandonedRow')?.classList.toggle('hidden',status!=='abandoned');
    if(slider){
      if(want)slider.value='0';
      else if(status==='finished')slider.value='100';
      else if(number(slider.value)>=100)slider.value='99';
      const label=document.querySelector('#bookProgressValue');
      if(label)label.textContent=slider.value+'%';
    }
    const finished=document.querySelector('#bookFinishDate'),abandoned=document.querySelector('#bookAbandonedDate');
    if(fillDates&&status==='finished'&&finished&&!finished.value)finished.value=today();
    if(fillDates&&status==='abandoned'&&abandoned&&!abandoned.value)abandoned.value=today();
  }

  function openEditor(id=0,initialStatus=''){
    const source=number(id)?parseNative(NexoNative.getBook(number(id)),{}):{};
    if(id&&!source.id){toast('Libro no encontrado.');refreshLibrary();return;}
    const status=source.status||initialStatus||(!Object.hasOwn(labels,filter)?'want':filter);
    const rating=ratingOf(source);
    const selectedCategory=source.id?categoryOf(source):(categoryFilter==='all'?'':categoryFilter);
    openModal(id?'Editar libro':'Nuevo libro',`<div class="modal-form book-editor">
      <div class="book-editor-toolbar"><span class="book-editor-toolbar-label">Datos del libro</span></div>
      <label>Título<input id="bookTitle" type="text" maxlength="250" value="${escAttr(source.title||'')}" placeholder="Título del libro" required></label>
      <label>Autor<input id="bookAuthor" type="text" maxlength="180" value="${escAttr(source.author||'')}" placeholder="Nombre del autor" required></label>
      <label>Categoría<select id="bookCategorySelect">${categoryOptions(selectedCategory)}</select></label>
      <label id="bookCategoryCustomRow" class="hidden">Nueva categoría<input id="bookCategoryCustom" type="text" maxlength="80" placeholder="Nombre de la categoría"></label>
      <label>Argumento / sinopsis <span class="optional">(opcional)</span><textarea id="bookSynopsis" rows="5" maxlength="20000" placeholder="¿De qué trata? Anótalo al añadir el libro para recordarlo después.">${esc(source.synopsis||'')}</textarea></label>
      <label>Estado de lectura<select id="bookStatus">
        ${Object.entries(labels).map(([key,label])=>`<option value="${key}" ${status===key?'selected':''}>${esc(label)}</option>`).join('')}
      </select></label>
      <div id="bookReadingFields">
        <label id="bookStartRow">Fecha de inicio <span class="optional">(opcional)</span><input id="bookStartDate" type="date" value="${escAttr(source.startDate||'')}"></label>
        <div class="book-slider-heading"><label for="bookProgress">Progreso de lectura</label><strong id="bookProgressValue">${number(source.progress)}%</strong></div>
        <input id="bookProgress" type="range" min="0" max="100" step="1" value="${number(source.progress)}">
        <div class="book-slider-ends"><small>0 % · Sin empezar</small><small>100 % · Terminado</small></div>
        <label id="bookFinishRow" class="hidden">Fecha de finalización<input id="bookFinishDate" type="date" value="${escAttr(source.finishDate||'')}"></label>
        <label id="bookAbandonedRow" class="hidden">Fecha de abandono<input id="bookAbandonedDate" type="date" value="${escAttr(source.abandonedDate||'')}"></label>
        <label>Mi resumen personal <span class="optional">(opcional)</span><textarea id="bookSummary" rows="5" maxlength="20000" placeholder="Ideas, conclusiones y cosas que quieras recordar…">${esc(source.summary||'')}</textarea></label>
        <button id="bookDictate" class="secondary-button book-dictate" type="button">${uiIcon('mic')} Dictar resumen</button>
        <small class="muted">El dictado se convierte a texto editable; no se almacena audio.</small>
      </div>
      <p class="book-form-error hidden" id="bookError" role="alert"></p>
      <div class="record-form-actions--compact book-editor-actions" role="group" aria-label="Acciones del editor de libros">
        <button id="bookCancel" class="secondary-button" type="button">${uiIcon('close')}<span>Cancelar</span></button>
        <button id="bookSave" class="primary-button nexo-unified-save" type="button" title="Guardar libro" aria-label="Guardar libro">${uiIcon('save')}</button>
      </div>
    </div>`);
    // El pie comparte la estructura desacoplada del scroll de los editores de eventos.
    document.querySelector('#modal .modal-sheet')?.appendChild(document.querySelector('.book-editor-actions'));
    editor={id:number(id),favorite:!!source.favorite,rating,scrollY:window.scrollY,baseline:'',sourceStatus:source.status||'',
      hadReadData:!!(source.progress||source.startDate||source.finishDate||source.abandonedDate||source.summary)};
    document.querySelector('#bookCategorySelect').addEventListener('change',updateCategoryCustom);
    document.querySelector('#bookStatus').addEventListener('change',()=>{
      const s=document.querySelector('#bookStatus').value;
      if(s==='reading'&&!document.querySelector('#bookStartDate').value&&
        (!source.id||source.status==='want'))document.querySelector('#bookStartDate').value=today();
      updateForm(true);
    });
    document.querySelector('#bookProgress').addEventListener('input',e=>{
      if(number(e.target.value)===100)document.querySelector('#bookStatus').value='finished';
      else if(document.querySelector('#bookStatus').value==='finished')document.querySelector('#bookStatus').value='reading';
      updateForm(true);
    });
    document.querySelector('#bookDictate').addEventListener('click',()=>{
      startManagedDictation('book-summary',{
        base:document.querySelector('#bookSummary')?.value||'',
        title:'DICTAR RESUMEN DEL LIBRO',hint:'Explica lo que te ha parecido el libro y pulsa TERMINAR.'
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
    if(document.querySelector('#bookCategorySelect')?.value==='__custom'&&!payload.category){
      error.textContent='Introduce el nombre de la nueva categoría.';error.classList.remove('hidden');
      document.querySelector('#bookCategoryCustom')?.focus();return;
    }
    const commit=()=>{
      const result=parseNative(NexoNative.saveBook(JSON.stringify(payload)),{ok:false});
      if(!result.ok){
        error.textContent=result.error||'No se pudo guardar el libro.';error.classList.remove('hidden');return;
      }
      const scrollY=editor.scrollY;
      editor=null;closeModal();
      if(document.querySelector('.books-library')){
        refreshLibrary();
        requestAnimationFrame(()=>window.scrollTo(0,scrollY));
      }else openPanel('books');
      toast('Libro guardado correctamente.');
    };
    if(payload.status==='want'&&editor.id&&editor.sourceStatus!=='want'&&editor.hadReadData){
      confirmGeneric('Volver a Quiero leer',
        'Este cambio reiniciará el progreso y borrará las fechas y el resumen. El corazón y las estrellas se conservarán. ¿Quieres continuar?',
        commit,'Mover a pendientes');
    }else commit();
  }

  function startBook(id){
    const b=books.find(x=>number(x.id)===id);
    if(!b||b.status!=='want')return;
    const r=parseNative(NexoNative.saveBook(JSON.stringify({
      ...b,status:'reading',progress:0,startDate:today(),finishDate:'',abandonedDate:'',summary:''
    })),{ok:false});
    if(!r.ok){toast(r.error||'No se pudo empezar la lectura.');return;}
    closeModal();filter='reading';search='';refreshLibrary();
    const searchInput=document.querySelector('#booksSearchInput');if(searchInput)searchInput.value='';
    toast('Libro movido a En lectura.');
  }

  function removeBook(id){
    const b=books.find(x=>number(x.id)===id);if(!b)return;
    confirmGeneric('Eliminar libro','¿Eliminar “'+b.title+'” de tu biblioteca?',()=>{
      const ok=NexoNative.deleteBook(id);toast(ok?'Libro eliminado.':'No se pudo eliminar el libro.');
      if(ok){closeModal();refreshLibrary();}
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
