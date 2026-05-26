export default function GamesPage() {
  const games = [
    { id: 1, title: "开心消消乐", cover: "", desc: "经典三消益智游戏" },
  ];

  return (
    <div className="games-page">
      <h2>我的游戏库</h2>
      <div className="cards-grid">
        {games.map((game) => (
          <div key={game.id} className="game-card placeholder">
            <div className="card-icon">🎮</div>
            <div className="card-title">{game.title}</div>
            <div className="card-desc">{game.desc}</div>
          </div>
        ))}
      </div>
    </div>
  );
}