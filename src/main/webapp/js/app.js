const clock = document.getElementById("clock");

function fit() {
  const availableWidth = document.documentElement.clientWidth * 0.98;
  const availableHeight = document.documentElement.clientHeight * 0.86;
  clock.style.fontSize = "100px";
  const scale = Math.min(availableWidth / clock.scrollWidth, availableHeight / clock.scrollHeight);
  clock.style.fontSize = Math.floor(100 * scale) + "px";
}

async function tick() {
  let delay = 1000;
  try {
    const response = await fetch("api/time", { cache: "no-store" });
    if (response.ok) {
      const time = (await response.text()).trim();
      clock.textContent = time;
      clock.dateTime = time;
      fit();
    }
    const requested = Number(response.headers.get("X-Delay-Millis"));
    if (Number.isFinite(requested) && requested > 0) {
      delay = requested;
    }
  } catch (error) {
    clock.textContent = "--:--:--";
  }
  window.setTimeout(tick, delay);
}

window.addEventListener("resize", fit);
tick();
