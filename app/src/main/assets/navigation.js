'use strict';

// Navigation has no persistence or database side effects. Each route owns its
// scroll position; opening a detail remembers the route it came from.
class NexoRouter {
  constructor(initial = {screen:'today'}) {
    this.current = {...initial};
    this.history = [];
    this.positions = new Map();
  }
  key(route) { return [route.screen,route.panel||'',route.list||''].join('/'); }
  enter(next, options = {}) {
    const previous = {...this.current,scrollY:Number(options.currentScroll)||0};
    this.positions.set(this.key(previous),previous.scrollY);
    const changed = this.key(previous)!==this.key(next);
    if(options.root) this.history=[];
    else if(changed&&!options.replace) {
      this.history.push(previous);
      if(this.history.length>30)this.history.shift();
    }
    this.current={...next};
    return {changed,scrollY:options.scrollY??this.positions.get(this.key(next))??0};
  }
  back() { return this.history.pop()||null; }
}
if(typeof module!=='undefined'&&module.exports)module.exports=NexoRouter;
