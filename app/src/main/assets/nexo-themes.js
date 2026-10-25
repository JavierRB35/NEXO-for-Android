/* Nexo Vivo: persistencia local EXCLUSIVA de apariencia, independiente de datos y funciones. */
(function(){
  'use strict';
  const KEY='nexo.visual.theme.v1';
  const THEMES=['vivo','mediterraneo','noche'];
  const STATUS_BAR={vivo:'#F6F8FF',mediterraneo:'#FFFAF1',noche:'#152237'};
  function valid(theme){return THEMES.includes(theme)?theme:'vivo';}
  function remembered(){
    try{return valid(window.localStorage.getItem(KEY));}
    catch(_){return 'vivo';}
  }
  let current=remembered();
  function reflect(){
    document.documentElement.dataset.nexoTheme=current;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content',STATUS_BAR[current]);
    document.querySelector('meta[name="color-scheme"]')?.setAttribute('content',current==='noche'?'dark':'light');
    document.querySelectorAll('[data-nexo-theme-choice]').forEach(button=>{
      const selected=button.dataset.nexoThemeChoice===current;
      button.classList.toggle('is-selected',selected);
      button.setAttribute('aria-pressed',String(selected));
    });
  }
  function select(theme){
    if(!THEMES.includes(theme))return false;
    current=theme;
    reflect();
    try{window.localStorage.setItem(KEY,current);}catch(_){}
    return true;
  }
  // Aplicar el tema antes de renderizar el primer fotograma: sin destello claro/oscuro.
  document.documentElement.dataset.nexoTheme=current;
  function bind(){
    reflect();
    document.querySelectorAll('[data-nexo-theme-choice]').forEach(button=>
      button.addEventListener('click',()=>select(button.dataset.nexoThemeChoice)));
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',bind,{once:true});
  else bind();
  window.NexoVisualTheme={get:()=>current,select};
})();
