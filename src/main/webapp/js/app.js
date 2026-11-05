const clock = document.getElementById("clock");

function pad(value) {
  return String(value).padStart(2, "0");
}

function render() {
  const now = new Date();
  const hours = pad(now.getHours());
  const minutes = pad(now.getMinutes());
  const seconds = pad(now.getSeconds());
  clock.textContent = hours + ":" + minutes + ":" + seconds;
  clock.dateTime = now.toISOString();
}

function tick() {
  render();
  window.setTimeout(tick, 1000 - (Date.now() % 1000));
}

render();
window.setTimeout(tick, 1000 - (Date.now() % 1000));
