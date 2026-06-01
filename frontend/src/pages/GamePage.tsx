import { useParams, useNavigate } from "react-router-dom";
import { lazy, Suspense } from "react";

const Match3Game = lazy(() => import("../components/Match3Game"));

const games: Record<string, { title: string; component: string }> = {
  "1": { title: "消消乐", component: "Match3" },
};

export default function GamePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const game = id ? games[id] : null;

  return (
    <div className="game-page">
      <header className="game-header">
        <button className="back-btn" onClick={() => navigate(-1)}>
          <svg viewBox="0 0 24 24">
            <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
          </svg>
          <span>返回</span>
        </button>
        <div className="game-title">{game?.title || "游戏"}</div>
        <div className="game-actions" />
      </header>
      
      <main className="game-content">
        {game ? (
          <div className="game-area">
            {game.component === "Match3" && (
              <Suspense fallback={<div className="loading">加载中...</div>}>
                <Match3Game />
              </Suspense>
            )}
          </div>
        ) : (
          <div className="game-not-found">
            <p>游戏不存在</p>
          </div>
        )}
      </main>
    </div>
  );
}