import assert from 'node:assert/strict';
import fs from 'node:fs';
import {JSDOM} from 'jsdom';

function exercise(file, moduleName, kind, preset, forbiddenPreset){
  const dom=new JSDOM('<main><div id="panelContent"></div><div id="modalBody"></div></main>',{
    url:'https://nexo.local/',runScripts:'outside-only',pretendToBeVisual:true
  });
  const {window}=dom,doc=window.document;
  let rows=[{
    id:1,title:'Antiguo sin categoría',author:'Autor',creator:'Creador',mediaType:'movie',
    status:'want',progress:0,startDate:'',finishDate:'',abandonedDate:'',summary:'',
    rating:3,favorite:true,updatedAt:1,category:''
  }];
  let nextId=2,shared='';
  const esc=v=>String(v??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  window.esc=esc;window.escAttr=v=>esc(v).replace(/"/g,'&quot;');
  window.parseNative=(raw,defaultValue)=>{try{return JSON.parse(raw);}catch{return defaultValue;}};
  window.uiIcon=name=>'<i>'+name+'</i>';
  window.toast=()=>{};
  window.requestAnimationFrame=fn=>fn();
  window.scrollTo=()=>{};
  window.openModal=(_title,html)=>{doc.querySelector('#modalBody').innerHTML=html;};
  window.closeModal=()=>{doc.querySelector('#modalBody').innerHTML='';};
  window.confirmGeneric=(_title,_body,callback)=>callback();
  window.startManagedDictation=()=>{};
  window.openPanel=()=>window[moduleName].render();
  const native={
    getAll:()=>JSON.stringify(rows),
    get:id=>JSON.stringify(rows.find(x=>x.id===id)||{}),
    save:json=>{
      const record=JSON.parse(json);
      if(!record.title)return JSON.stringify({ok:false,error:'Título requerido'});
      const found=rows.findIndex(x=>x.id===record.id);
      if(found>=0)rows[found]={...record,updatedAt:Date.now()};
      else {record.id=nextId++;rows.push({...record,updatedAt:Date.now()});}
      return JSON.stringify({ok:true,id:record.id});
    },
    delete:id=>{const n=rows.length;rows=rows.filter(x=>x.id!==id);return rows.length<n;},
    sharePlainText:(_title,text)=>{shared=text;}
  };
  window.NexoNative={...native,
    getBooks:native.getAll,getBook:native.get,saveBook:native.save,deleteBook:native.delete,
    getMediaItems:native.getAll,getMediaItem:native.get,saveMediaItem:native.save,deleteMediaItem:native.delete
  };
  window.eval(fs.readFileSync(file,'utf8'));
  const library=window[moduleName];
  library.render();
  const getIds=()=>[...doc.querySelectorAll('[data-book-open]')].map(n=>Number(n.dataset.bookOpen));
  const chooseCategory=name=>{
    const filter=doc.querySelector('#booksCategoryFilter');
    filter.value=name;filter.dispatchEvent(new window.Event('change'));
  };

  // Las entradas anteriores permanecen sin categoría y mantienen su valoración.
  assert.equal(rows[0].category,'');
  assert.equal(rows[0].rating,3);
  assert.equal(doc.querySelector('#booksCategoryFilter option[value=""]').textContent.includes('Sin categoría'),true);
  chooseCategory('');
  assert.deepEqual(getIds(),[1]);
  chooseCategory('all');
  library.openEditor();
  const categorySelect=doc.querySelector('#bookCategorySelect');
  assert([...categorySelect.options].some(opt=>opt.value===preset),'Debe existir categoría propia');
  assert(![...categorySelect.options].some(opt=>opt.value===forbiddenPreset),
    'Una categoría exclusiva del otro módulo no debe aparecer');
  categorySelect.value='__custom';
  categorySelect.dispatchEvent(new window.Event('change'));
  assert.equal(doc.querySelector('#bookCategoryCustomRow').classList.contains('hidden'),false);
  doc.querySelector('#bookTitle').value='Registro personalizado';
  doc.querySelector('#bookAuthor').value=kind==='books'?'Autora de prueba':'';
  doc.querySelector('#bookCategoryCustom').value=kind==='books'?'Mis lecturas técnicas':'Series para compartir';
  if(kind==='media')doc.querySelector('#mediaKind').value='series';
  doc.querySelector('#bookSave').click();
  assert.equal(rows.length,2,'Alta válida con categoría personalizada');
  const selected=rows.find(r=>r.id===2);
  const label=selected.category;
  assert.equal(label,kind==='books'?'Mis lecturas técnicas':'Series para compartir');
  assert(doc.querySelector('#booksCategoryFilter').innerHTML.includes(label));
  chooseCategory(label);
  assert.deepEqual(getIds(),[2]);
  assert(doc.querySelector('.book-category-chip').textContent.includes(label));
  // Categoría, estado y tipo se combinan sin perder el resto.
  doc.querySelector('[data-books-filter="finished"]').click();
  assert.deepEqual(getIds(),[]);
  doc.querySelector('[data-books-filter="all"]').click();
  if(kind==='media'){
    doc.querySelector('[data-media-type-filter="movie"]').click();
    assert.deepEqual(getIds(),[]);
    doc.querySelector('[data-media-type-filter="series"]').click();
    assert.deepEqual(getIds(),[2]);
  }
  doc.querySelector('[data-book-open]').click();
  assert(doc.querySelector('.book-detail').textContent.includes(label));
  doc.querySelector('#bookDetailShare').click();
  assert(shared.includes('Categoría: '+label));
  doc.querySelector('#bookDetailFavorite').click();
  assert.equal(rows.find(r=>r.id===2).category,label,'Favorito no modifica categoría');
  doc.querySelector('#bookDetailEdit').click();
  assert.equal(doc.querySelector('#bookCategorySelect').value,label,'La categoría personalizada se puede reabrir');
  doc.querySelector('#bookCategorySelect').value=preset;
  doc.querySelector('#bookSave').click();
  assert.equal(rows.find(r=>r.id===2).category,preset,'Se puede reclasificar sin crear otro registro');
  assert.equal(rows.length,2);
  chooseCategory(preset);
  assert.deepEqual(getIds(),[2]);
  library.openEditor(1);
  assert.equal(doc.querySelector('#bookCategorySelect').value,'','Las fichas antiguas conservan Sin categoría');
  assert.equal(rows[0].rating,3);
  console.log('OK: categorías '+kind+' (presets propios, personalizados, filtros, conservación, reclasificar)');
}
exercise('app/src/main/assets/books.js','NexoBooks','books','Novela','Documental');
exercise('app/src/main/assets/media.js','NexoMedia','media','Documental','Novela');
