/* ============================================================
   Mixi — lógica de la interfaz
   Corre en dos modos:
     · Dentro del APK  → usa window.Mixi (el motor nativo)
     · En el navegador → modo demo (simula la descarga)
   ============================================================ */

(() => {
  'use strict';

  const EN_APK = typeof window.Mixi !== 'undefined';

  const $ = (id) => document.getElementById(id);

  const campo       = $('link');
  const btnPegar    = $('paste');
  const btnVideo    = $('btn-video');
  const btnAudio    = $('btn-audio');
  const cajaProg    = $('progress');
  const lblProg     = $('progress-label');
  const txtPct      = $('progress-pct');
  const barra       = $('progress-bar');
  const cajaMsg     = $('message');
  const txtMsg      = $('message-text');
  const btnAccion   = $('message-action');
  const lista       = $('history-list');
  const vacio       = $('history-empty');

  const CLAVE_HISTORIAL = 'mixi_historial';
  const MAX_HISTORIAL   = 40;

  /* ---------- utilidades ---------- */

  function esLinkValido(texto) {
    try {
      const u = new URL(texto);
      return /(^|\.)youtube\.com$|(^|\.)youtu\.be$/.test(u.hostname);
    } catch {
      return false;
    }
  }

  function ocultar(el) { el.hidden = true; }
  function mostrar(el) { el.hidden = false; }

  function limpiarMensaje() {
    ocultar(cajaMsg);
    btnAccion.hidden = true;
    btnAccion.onclick = null;
  }

  function mostrarMensaje(texto, tipo = 'error', accion = null) {
    txtMsg.textContent = texto;
    cajaMsg.className = 'message ' + tipo;
    mostrar(cajaMsg);

    if (accion) {
      btnAccion.textContent = accion.etiqueta;
      btnAccion.onclick = accion.alHacer;
      mostrar(btnAccion);
    } else {
      btnAccion.hidden = true;
      btnAccion.onclick = null;
    }
  }

  function bloquearBotones(si) {
    [btnVideo, btnAudio, btnPegar].forEach((b) => { b.disabled = si; });
  }

  /* ---------- progreso ---------- */

  function pintarProgreso(pct, etiqueta = 'Descargando…') {
    const n = Math.max(0, Math.min(100, Math.round(pct)));
    lblProg.textContent = etiqueta;
    txtPct.textContent = n + '%';
    barra.style.width = n + '%';
    mostrar(cajaProg);
  }

  /* ---------- historial ---------- */

  function leerHistorial() {
    try {
      return JSON.parse(localStorage.getItem(CLAVE_HISTORIAL)) || [];
    } catch {
      return [];
    }
  }

  function guardarEnHistorial(nombre, tipo) {
    const items = leerHistorial();
    items.unshift({ nombre, tipo, fecha: Date.now() });
    localStorage.setItem(
      CLAVE_HISTORIAL,
      JSON.stringify(items.slice(0, MAX_HISTORIAL))
    );
    pintarHistorial();
  }

  function pintarHistorial() {
    const items = leerHistorial();
    lista.innerHTML = '';
    vacio.hidden = items.length > 0;

    for (const it of items) {
      const li = document.createElement('li');

      const ico = document.createElement('span');
      ico.className = 'tipo';
      ico.textContent = it.tipo === 'audio' ? '🎵' : '🎬';

      const nom = document.createElement('span');
      nom.className = 'nombre';
      /* sin extensión (.m4a y .mp4 confunden) y con espacios en vez de guiones */
      nom.textContent = String(it.nombre)
        .replace(/\.[^.]+$/, '')
        .replace(/[-_]+/g, ' ')
        .trim();

      const fec = document.createElement('span');
      fec.className = 'fecha';
      fec.textContent = new Date(it.fecha).toLocaleDateString('es-CO',
        { day: '2-digit', month: 'short' });

      li.append(ico, nom, fec);
      lista.appendChild(li);
    }
  }

  /* ---------- API que el APK llama para pintar ---------- */

  window.MixiUI = {
    progress(pct) {
      pintarProgreso(pct);
    },
    done(nombre) {
      pintarProgreso(100, '¡Listo!');
      setTimeout(() => { ocultar(cajaProg); }, 900);
      guardarEnHistorial(nombre || 'Descarga', estadoUltimoTipo);
      limpiarMensaje();
      bloquearBotones(false);
      campo.value = '';
    },
    error(mensaje) {
      ocultar(cajaProg);
      bloquearBotones(false);
      mostrarMensaje(mensaje, 'error', {
        etiqueta: '🔄 ACTUALIZAR',
        alHacer: () => {
          if (EN_APK) window.Mixi.checkAppUpdate();
        },
      });
    },
  };

  /* ---------- descarga ---------- */

  let estadoUltimoTipo = 'video';

  function descargar(tipo) {
    const url = campo.value.trim();

    limpiarMensaje();

    if (!url) {
      return mostrarMensaje('Primero pega el enlace del video 😊');
    }

    if (!esLinkValido(url)) {
      return mostrarMensaje('Ese enlace no sirve. Copia otro 😕');
    }

    estadoUltimoTipo = tipo;
    bloquearBotones(true);
    pintarProgreso(0);

    if (EN_APK) {
      if (tipo === 'video') window.Mixi.downloadVideo(url);
      else window.Mixi.downloadAudio(url);
    } else {
      simularDescarga(tipo);
    }
  }

  /* Simulación solo para probar el diseño sin el APK */
  function simularDescarga(tipo) {
    let pct = 0;
    const nombre = tipo === 'audio'
      ? 'alabanza-de-prueba.m4a'
      : 'predicacion-de-prueba.mp4';

    const reloj = setInterval(() => {
      pct += Math.random() * 14 + 4;
      if (pct >= 100) {
        clearInterval(reloj);
        window.MixiUI.done(nombre);
      } else {
        pintarProgreso(pct, tipo === 'audio' ? 'Descargando el audio…' : 'Descargando el video…');
      }
    }, 320);
  }

  /* ---------- pegar del portapapeles ---------- */

  async function pegar() {
    limpiarMensaje();

    // Dentro del APK: lectura directa, sin diálogos del sistema
    if (EN_APK && window.Mixi.getClipboardText) {
      const t = window.Mixi.getClipboardText();
      if (t) campo.value = t.trim();
      return;
    }

    try {
      campo.value = (await navigator.clipboard.readText()).trim();
    } catch {
      campo.focus();
    }
  }

  /* ---------- eventos ---------- */

  btnVideo.addEventListener('click', () => descargar('video'));
  btnAudio.addEventListener('click', () => descargar('audio'));
  btnPegar.addEventListener('click', pegar);

  campo.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') descargar('video');
  });

  campo.addEventListener('input', limpiarMensaje);

  /* Al abrir: si el APK ya tiene un link en el portapapeles, llenarlo solo */
  window.addEventListener('load', () => {
    pintarHistorial();
    if (EN_APK && window.Mixi.getClipboardText) {
      const t = window.Mixi.getClipboardText();
      if (t && esLinkValido(t.trim())) campo.value = t.trim();
    }
  });
})();
