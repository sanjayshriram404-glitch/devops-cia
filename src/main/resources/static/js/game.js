// Two-player X/O (tic-tac-toe). The game rules are pure functions so they can be tested on their own.
(function (root) {
    "use strict";

    const LINES = [
        [0, 1, 2], [3, 4, 5], [6, 7, 8], // rows
        [0, 3, 6], [1, 4, 7], [2, 5, 8], // columns
        [0, 4, 8], [2, 4, 6],            // diagonals
    ];

    /** Returns {mark, line} for a finished row, "draw" for a full board, or null while play continues. */
    function result(board) {
        for (const line of LINES) {
            const [a, b, c] = line;
            if (board[a] && board[a] === board[b] && board[a] === board[c]) {
                return { mark: board[a], line };
            }
        }
        return board.every(Boolean) ? "draw" : null;
    }

    const rules = { LINES, result };
    if (typeof module !== "undefined" && module.exports) {
        module.exports = rules; // lets Node run the rules without a browser
        return;
    }

    // ---------- Browser UI ----------
    const boardEl = document.getElementById("board");
    const statusEl = document.getElementById("status");
    const scoreEls = {
        X: document.getElementById("score-x"),
        O: document.getElementById("score-o"),
        draw: document.getElementById("score-draw"),
    };

    let board = Array(9).fill(null);
    let turn = "X";
    let over = false;
    const scores = { X: 0, O: 0, draw: 0 };

    const cells = Array.from({ length: 9 }, (_, i) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "cell";
        b.addEventListener("click", () => play(i));
        boardEl.append(b);
        return b;
    });

    function play(i) {
        if (over || board[i]) return;
        board[i] = turn;
        const outcome = result(board);
        if (outcome === "draw") {
            over = true;
            scores.draw++;
        } else if (outcome) {
            over = true;
            scores[outcome.mark]++;
        } else {
            turn = turn === "X" ? "O" : "X";
        }
        render(outcome, i);
    }

    function render(outcome, justPlaced) {
        cells.forEach((cell, i) => {
            const mark = board[i];
            cell.textContent = mark || "";
            cell.dataset.mark = mark || "";
            cell.disabled = over || Boolean(mark);
            cell.classList.toggle("winning", Boolean(outcome && outcome.line && outcome.line.includes(i)));
            cell.classList.toggle("placed", i === justPlaced);
            const row = Math.floor(i / 3) + 1;
            const col = (i % 3) + 1;
            cell.setAttribute("aria-label", `Row ${row}, column ${col}, ${mark || "empty"}`);
        });

        statusEl.classList.toggle("win", Boolean(outcome && outcome.mark));
        if (outcome === "draw") statusEl.textContent = "It's a draw";
        else if (outcome) statusEl.textContent = `${outcome.mark} wins`;
        else statusEl.textContent = `${turn} to play`;

        scoreEls.X.textContent = scores.X;
        scoreEls.O.textContent = scores.O;
        scoreEls.draw.textContent = scores.draw;
    }

    function newGame() {
        board = Array(9).fill(null);
        turn = "X";
        over = false;
        render(null, -1);
        cells[0].focus();
    }

    document.getElementById("new-game").addEventListener("click", newGame);
    document.getElementById("reset-scores").addEventListener("click", () => {
        scores.X = scores.O = scores.draw = 0;
        newGame();
    });

    render(null, -1);
})(this);
