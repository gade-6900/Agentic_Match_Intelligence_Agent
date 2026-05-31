import { useState, useEffect, useRef } from 'react'
import './App.css'

function App() {
  const [matches, setMatches] = useState([])
  const [selectedMatchId, setSelectedMatchId] = useState(null)
  const [matchDetails, setMatchDetails] = useState(null)
  const [playerStats, setPlayerStats] = useState([])
  const [commentary, setCommentary] = useState([])
  
  const [chatMessages, setChatMessages] = useState([])
  const [chatInput, setChatInput] = useState('')
  const [isChatLoading, setIsChatLoading] = useState(false)
  
  const [activeTab, setActiveTab] = useState('commentary')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [team1, setTeam1] = useState('Chennai Super Kings')
  const [team2, setTeam2] = useState('Mumbai Indians')
  
  const chatEndRef = useRef(null)
  const BACKEND_URL = 'http://localhost:8080'

  const teams = [
    'Chennai Super Kings',
    'Mumbai Indians',
    'Royal Challengers Bangalore',
    'Kolkata Knight Riders'
  ]

  // Fetch match list on dashboard load
  useEffect(() => {
    fetchMatches()
    const interval = setInterval(fetchMatches, 5000)
    return () => clearInterval(interval)
  }, [])

  // Poll current match data if active
  useEffect(() => {
    if (!selectedMatchId) return

    fetchMatchData()
    const interval = setInterval(() => {
      fetchMatchData()
    }, 3000)

    // Reset chat messages when opening a new match
    setChatMessages([
      { sender: 'ai', text: 'Hello! I am your IPL Live Match AI Assistant. Ask me anything about this match, player performances, or get a live win prediction!' }
    ])

    return () => clearInterval(interval)
  }, [selectedMatchId])

  // Scroll to bottom of chat
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMessages])

  const fetchMatches = async () => {
    try {
      const res = await fetch(`${BACKEND_URL}/api/matches`)
      const data = await res.json()
      setMatches(data)
    } catch (err) {
      console.error('Error fetching matches:', err)
    }
  }

  const fetchMatchData = async () => {
    if (!selectedMatchId) return
    try {
      // 1. Fetch scores & details
      const detailRes = await fetch(`${BACKEND_URL}/api/matches/${selectedMatchId}`)
      if (detailRes.ok) {
        const detailData = await detailRes.json()
        setMatchDetails(detailData)
        
        // Auto-switch tabs based on status
        if (detailData.matchInfo.status === 'COMPLETED' && activeTab === 'preview') {
          setActiveTab('summary')
        }
      }

      // 2. Fetch stats
      const statsRes = await fetch(`${BACKEND_URL}/api/matches/${selectedMatchId}/stats`)
      if (statsRes.ok) {
        const statsData = await statsRes.json()
        setPlayerStats(statsData)
      }

      // 3. Fetch commentary
      const commRes = await fetch(`${BACKEND_URL}/api/matches/${selectedMatchId}/commentary`)
      if (commRes.ok) {
        const commData = await commRes.json()
        setCommentary(commData)
      }
    } catch (err) {
      console.error('Error fetching match details:', err)
    }
  }

  const handleCreateMatch = async (e) => {
    e.preventDefault()
    if (team1 === team2) {
      alert('Please select two different teams!')
      return
    }
    try {
      const res = await fetch(`${BACKEND_URL}/api/matches/simulate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ team1, team2 })
      })
      if (res.ok) {
        const data = await res.json()
        setSelectedMatchId(data.id)
        setShowCreateModal(false)
        setActiveTab('preview')
        fetchMatches()
      }
    } catch (err) {
      console.error('Error creating match:', err)
    }
  }

  const handleNextBall = async () => {
    if (!selectedMatchId) return
    try {
      await fetch(`${BACKEND_URL}/api/matches/${selectedMatchId}/next-ball`, {
        method: 'POST'
      })
      fetchMatchData()
    } catch (err) {
      console.error('Error triggering next ball:', err)
    }
  }

  const handleResetMatch = async () => {
    if (!selectedMatchId) return
    if (!confirm('Are you sure you want to reset this match? This clears all ball history.')) return
    try {
      await fetch(`${BACKEND_URL}/api/matches/${selectedMatchId}/reset`, {
        method: 'POST'
      })
      fetchMatchData()
    } catch (err) {
      console.error('Error resetting match:', err)
    }
  }

  const handleResetAll = async () => {
    if (!confirm('Are you sure you want to clear the entire database? All matches will be deleted.')) return
    try {
      await fetch(`${BACKEND_URL}/api/matches/reset-all`, {
        method: 'POST'
      })
      setSelectedMatchId(null)
      setMatchDetails(null)
      fetchMatches()
    } catch (err) {
      console.error('Error clearing database:', err)
    }
  }

  const handleSendMessage = async (e) => {
    e.preventDefault()
    if (!chatInput.trim() || !selectedMatchId) return

    const userMsg = chatInput.trim()
    setChatMessages(prev => [...prev, { sender: 'user', text: userMsg }])
    setChatInput('')
    setIsChatLoading(true)

    try {
      const res = await fetch(`${BACKEND_URL}/api/matches/${selectedMatchId}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: userMsg })
      })
      if (res.ok) {
        const data = await res.json()
        setChatMessages(prev => [...prev, { sender: 'ai', text: data.reply }])
      } else {
        setChatMessages(prev => [...prev, { sender: 'ai', text: 'Sorry, I had trouble parsing the match context. Please try again!' }])
      }
    } catch (err) {
      console.error('Error communicating with chatbot:', err)
      setChatMessages(prev => [...prev, { sender: 'ai', text: 'Could not connect to the assistant server.' }])
    } finally {
      setIsChatLoading(false)
    }
  }

  const getTeamScore = (teamName) => {
    if (!matchDetails || !matchDetails.scores) return null
    return matchDetails.scores.find(s => s.team === teamName)
  }

  // Format Over count (e.g. 15 overs 3 balls -> 15.3)
  const formatOvers = (overs, balls) => {
    return `${overs}.${balls}`
  }

  // Calculate required run rate
  const getRequiredRunRate = () => {
    if (!matchDetails) return null
    const { matchInfo } = matchDetails
    if (matchInfo.currentInnings !== 2 || !matchInfo.targetRuns) return null

    const battingScore = matchDetails.scores.find(s => s.isBatting)
    if (!battingScore) return null

    const runsNeeded = matchInfo.targetRuns - battingScore.runs
    const ballsBowled = battingScore.overs * 6 + battingScore.balls
    const ballsRemaining = 120 - ballsBowled

    if (ballsRemaining <= 0) return 0.00
    return ((runsNeeded * 6) / ballsRemaining).toFixed(2)
  }

  // Calculate current run rate
  const getCurrentRunRate = () => {
    if (!matchDetails) return '0.00'
    const battingScore = matchDetails.scores.find(s => s.isBatting)
    if (!battingScore) return '0.00'

    const totalBalls = battingScore.overs * 6 + battingScore.balls
    if (totalBalls === 0) return '0.00'
    return ((battingScore.runs * 6) / totalBalls).toFixed(2)
  }

  // Custom markdown simple formatter for chat replies
  const renderFormattedText = (text) => {
    if (!text) return ''
    
    // Split by newlines to render paragraphs/lists
    const lines = text.split('\n')
    return lines.map((line, idx) => {
      let content = line

      // Handle Headers (e.g. ### Header)
      if (content.startsWith('### ')) {
        return <h4 key={idx} style={{ margin: '12px 0 6px', color: 'var(--primary)', fontSize: '1.1rem' }}>{content.replace('### ', '')}</h4>
      }
      if (content.startsWith('#### ')) {
        return <h5 key={idx} style={{ margin: '10px 0 4px', color: 'var(--text-header)', fontSize: '1rem' }}>{content.replace('#### ', '')}</h5>
      }
      
      // Handle Bold (e.g. **bold**)
      const boldRegex = /\*\*(.*?)\*\*/g
      let match
      const parts = []
      let lastIndex = 0
      
      while ((match = boldRegex.exec(content)) !== null) {
        if (match.index > lastIndex) {
          parts.push(content.substring(lastIndex, match.index))
        }
        parts.push(<strong key={match.index} style={{ color: 'var(--text-header)' }}>{match[1]}</strong>)
        lastIndex = boldRegex.lastIndex
      }
      if (lastIndex < content.length) {
        parts.push(content.substring(lastIndex))
      }

      const finalContent = parts.length > 0 ? parts : content

      // Handle Bullet Points
      if (line.trim().startsWith('- ') || line.trim().startsWith('* ')) {
        return <li key={idx} style={{ marginLeft: '16px', marginBottom: '4px' }}>{parts.length > 0 ? parts : line.replace(/^[-*]\s+/, '')}</li>
      }

      return <p key={idx} style={{ margin: '4px 0 8px', lineHeight: '1.4' }}>{finalContent}</p>
    })
  }

  return (
    <div className="app-container">
      {/* Header Bar */}
      <header className="main-header">
        <div className="header-logo">
          <span className="logo-icon">⚡</span>
          <h1>IPL Match Intelligence</h1>
        </div>
        <div className="header-actions">
          <button className="btn btn-secondary" onClick={handleResetAll}>Clear DB</button>
          <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>+ New Simulation</button>
        </div>
      </header>

      {/* Main Content Layout */}
      {!selectedMatchId ? (
        // DASHBOARD VIEW
        <main className="dashboard">
          <div className="hero-banner">
            <h2>Live AI Cricket Simulator & Assistant</h2>
            <p>Simulate realistic IPL fixtures ball-by-ball. Experience real-time AI-generated commentary and chat with our Gemini-powered bot about strategy, stats, and live odds!</p>
          </div>

          <div className="section-header">
            <h3>Match Dashboard</h3>
            <span className="badge">{matches.length} Matches</span>
          </div>

          {matches.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">🏏</div>
              <h4>No Active Simulations</h4>
              <p>Create a live match simulation between top IPL franchises to get started.</p>
              <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>Start Simulation</button>
            </div>
          ) : (
            <div className="match-grid">
              {matches.map(m => {
                // Find scores for this match
                const mScores = m.status !== 'UPCOMING' ? matches.find(match => match.id === m.id) : null // mock score
                return (
                  <div key={m.id} className={`match-card ${m.status.toLowerCase()}`} onClick={() => setSelectedMatchId(m.id)}>
                    <div className="card-header">
                      <span className={`status-indicator ${m.status.toLowerCase()}`}>
                        {m.status === 'IN_PROGRESS' ? '● LIVE' : m.status}
                      </span>
                      {m.currentInnings && <span className="innings-badge">Innings {m.currentInnings}</span>}
                    </div>
                    
                    <div className="card-teams">
                      <div className="team-row">
                        <span className="team-name">{m.team1}</span>
                      </div>
                      <div className="team-row">
                        <span className="team-name">{m.team2}</span>
                      </div>
                    </div>

                    <div className="card-info">
                      {m.status === 'COMPLETED' ? (
                        <div className="result-text">🏆 {m.winner} won by {m.margin}</div>
                      ) : m.status === 'IN_PROGRESS' ? (
                        <div className="live-update">Click to view live scoreboard & AI insights</div>
                      ) : (
                        <div className="upcoming-text">Match Preview Available</div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </main>
      ) : (
        // MATCH CENTER VIEW
        <div className="match-center-layout">
          {/* Main Game Screen */}
          <main className="match-main">
            <button className="btn btn-back" onClick={() => setSelectedMatchId(null)}>
              ← Back to Dashboard
            </button>

            {matchDetails && (
              <>
                {/* Live scoreboard banner */}
                <section className="live-score-banner">
                  <div className="banner-top">
                    <div className="team-score-block">
                      <h3>{matchDetails.matchInfo.team1}</h3>
                      {getTeamScore(matchDetails.matchInfo.team1) && (
                        <div className="score-val">
                          {getTeamScore(matchDetails.matchInfo.team1).runs}/{getTeamScore(matchDetails.matchInfo.team1).wickets}
                          <span className="overs-val">
                            ({formatOvers(getTeamScore(matchDetails.matchInfo.team1).overs, getTeamScore(matchDetails.matchInfo.team1).balls)} ov)
                          </span>
                          {getTeamScore(matchDetails.matchInfo.team1).isBatting && <span className="batting-dot">●</span>}
                        </div>
                      )}
                    </div>

                    <div className="vs-badge">VS</div>

                    <div className="team-score-block text-right">
                      <h3>{matchDetails.matchInfo.team2}</h3>
                      {getTeamScore(matchDetails.matchInfo.team2) && (
                        <div className="score-val">
                          {getTeamScore(matchDetails.matchInfo.team2).isBatting && <span className="batting-dot">●</span>}
                          {getTeamScore(matchDetails.matchInfo.team2).runs}/{getTeamScore(matchDetails.matchInfo.team2).wickets}
                          <span className="overs-val">
                            ({formatOvers(getTeamScore(matchDetails.matchInfo.team2).overs, getTeamScore(matchDetails.matchInfo.team2).balls)} ov)
                          </span>
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="banner-bottom">
                    <div className="live-status-details">
                      {matchDetails.matchInfo.status === 'COMPLETED' ? (
                        <div className="winner-declaration">
                          🎉 <strong>{matchDetails.matchInfo.winner}</strong> won by {matchDetails.matchInfo.margin}
                        </div>
                      ) : (
                        <div className="chase-details">
                          {matchDetails.matchInfo.currentInnings === 2 ? (
                            <>
                              <strong>{matchDetails.scores.find(s => s.isBatting)?.team}</strong> needs{' '}
                              <strong>{matchDetails.matchInfo.targetRuns - matchDetails.scores.find(s => s.isBatting)?.runs}</strong> runs to win
                              <span className="runrate-info">
                                (CRR: {getCurrentRunRate()} | RRR: {getRequiredRunRate()})
                              </span>
                            </>
                          ) : (
                            <>
                              First Innings in progress (Current Run Rate: {getCurrentRunRate()})
                            </>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Admin trigger button */}
                    {matchDetails.matchInfo.status === 'IN_PROGRESS' && (
                      <div className="admin-actions">
                        <button className="btn btn-success btn-pulse" onClick={handleNextBall}>
                          ⚡ Next Ball
                        </button>
                        <button className="btn btn-secondary" onClick={handleResetMatch}>
                          🔄 Reset Match
                        </button>
                      </div>
                    )}
                  </div>
                </section>

                {/* Tabs selection */}
                <div className="tab-menu">
                  <button className={`tab-link ${activeTab === 'commentary' ? 'active' : ''}`} onClick={() => setActiveTab('commentary')}>
                    🎙️ AI Commentary
                  </button>
                  <button className={`tab-link ${activeTab === 'scorecard' ? 'active' : ''}`} onClick={() => setActiveTab('scorecard')}>
                    📊 Scorecard
                  </button>
                  {matchDetails.matchInfo.aiPreview && (
                    <button className={`tab-link ${activeTab === 'preview' ? 'active' : ''}`} onClick={() => setActiveTab('preview')}>
                      📅 Pre-match Preview
                    </button>
                  )}
                  {matchDetails.matchInfo.status === 'COMPLETED' && matchDetails.matchInfo.aiSummary && (
                    <button className={`tab-link ${activeTab === 'summary' ? 'active' : ''}`} onClick={() => setActiveTab('summary')}>
                      🏆 Post-match Summary
                    </button>
                  )}
                </div>

                {/* Tab content area */}
                <div className="tab-container">
                  {activeTab === 'commentary' && (
                    <div className="commentary-list">
                      {commentary.length === 0 ? (
                        <div className="commentary-empty">
                          <p>The match has not started yet. Tap <strong>⚡ Next Ball</strong> to simulate the first delivery!</p>
                        </div>
                      ) : (
                        commentary.map((ball, i) => {
                          const isWicket = ball.wicketType && ball.wicketType !== '';
                          const isBoundary = ball.runsScored === 4 || ball.runsScored === 6;
                          return (
                            <div key={ball.id} className={`commentary-row ${isWicket ? 'wicket-ball' : ''} ${isBoundary ? 'boundary-ball' : ''}`}>
                              <div className="ball-marker">
                                <span className="ball-index">{ball.innings}.{ball.overNum}.{ball.ballNum}</span>
                                <span className={`ball-outcome-badge runs-${ball.runsScored} ${isWicket ? 'outcome-wicket' : ''}`}>
                                  {isWicket ? 'W' : ball.extraRuns > 0 ? 'WD' : ball.runsScored}
                                </span>
                              </div>
                              <div className="ball-details">
                                <div className="ball- matchup">
                                  <strong>{ball.bowler}</strong> to <strong>{ball.batter}</strong>
                                </div>
                                <p className="commentary-text">{ball.commentary}</p>
                              </div>
                            </div>
                          )
                        })
                      )}
                    </div>
                  )}

                  {activeTab === 'scorecard' && (
                    <div className="scorecard-wrapper">
                      {/* Batting scorecard */}
                      <div className="scorecard-section">
                        <h4>Batting Card - {matchDetails.scores.find(s => s.isBatting)?.team || matchDetails.matchInfo.team1}</h4>
                        <table className="scorecard-table">
                          <thead>
                            <tr>
                              <th>Batter</th>
                              <th>Runs</th>
                              <th>Balls</th>
                              <th>4s</th>
                              <th>6s</th>
                              <th>SR</th>
                            </tr>
                          </thead>
                          <tbody>
                            {playerStats
                              .filter(s => s.teamName === (matchDetails.scores.find(score => score.isBatting)?.team || matchDetails.matchInfo.team1))
                              .map(s => {
                                const isDismissed = commentary.some(b => b.dismissedBatter === s.playerName);
                                const hasBatted = s.ballsFaced > 0;
                                return (
                                  <tr key={s.id} className={hasBatted && !isDismissed ? 'currently-batting' : ''}>
                                    <td>
                                      {s.playerName} {!isDismissed && hasBatted && '🏏'}
                                      <span className="player-role-label">{s.role}</span>
                                    </td>
                                    <td>{hasBatted ? s.runsScored : '-'}</td>
                                    <td>{hasBatted ? s.ballsFaced : '-'}</td>
                                    <td>{hasBatted ? s.fours : '-'}</td>
                                    <td>{hasBatted ? s.sixes : '-'}</td>
                                    <td>{hasBatted ? s.strikeRate.toFixed(1) : '-'}</td>
                                  </tr>
                                )
                              })}
                          </tbody>
                        </table>
                      </div>

                      {/* Bowling scorecard */}
                      <div className="scorecard-section">
                        <h4>Bowling Card - {matchDetails.scores.find(s => !s.isBatting)?.team || matchDetails.matchInfo.team2}</h4>
                        <table className="scorecard-table">
                          <thead>
                            <tr>
                              <th>Bowler</th>
                              <th>Overs</th>
                              <th>Runs</th>
                              <th>Wickets</th>
                              <th>Econ</th>
                            </tr>
                          </thead>
                          <tbody>
                            {playerStats
                              .filter(s => s.teamName === (matchDetails.scores.find(score => !score.isBatting)?.team || matchDetails.matchInfo.team2))
                              .filter(s => s.oversBowled > 0.0)
                              .map(s => (
                                <tr key={s.id}>
                                  <td>{s.playerName} <span className="player-role-label">{s.role}</span></td>
                                  <td>{s.oversBowled.toFixed(1)}</td>
                                  <td>{s.runsConceded}</td>
                                  <td>{s.wicketsTaken}</td>
                                  <td>{s.economyRate.toFixed(1)}</td>
                                </tr>
                              ))}
                            {playerStats
                              .filter(s => s.teamName === (matchDetails.scores.find(score => !score.isBatting)?.team || matchDetails.matchInfo.team2))
                              .filter(s => s.oversBowled === 0.0)
                              .map(s => (
                                <tr key={s.id} className="did-not-bowl">
                                  <td>{s.playerName} <span className="player-role-label">{s.role}</span></td>
                                  <td>-</td>
                                  <td>-</td>
                                  <td>-</td>
                                  <td>-</td>
                                </tr>
                              ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}

                  {activeTab === 'preview' && (
                    <div className="preview-container text-left">
                      {renderFormattedText(matchDetails.matchInfo.aiPreview)}
                    </div>
                  )}

                  {activeTab === 'summary' && (
                    <div className="summary-container text-left">
                      {renderFormattedText(matchDetails.matchInfo.aiSummary)}
                    </div>
                  )}
                </div>
              </>
            )}
          </main>

          {/* AI Assistant Chat Sidebar */}
          <aside className="chat-sidebar">
            <div className="chat-header">
              <span className="ai-badge">🤖 AI Match Assistant</span>
              <p>Ask anything about squads, player stats, or match situations!</p>
            </div>
            
            <div className="chat-body">
              {chatMessages.map((msg, i) => (
                <div key={i} className={`chat-message ${msg.sender}`}>
                  <div className="message-bubble">
                    {msg.sender === 'ai' ? renderFormattedText(msg.text) : msg.text}
                  </div>
                </div>
              ))}
              {isChatLoading && (
                <div className="chat-message ai">
                  <div className="message-bubble typing-loader">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              )}
              <div ref={chatEndRef} />
            </div>

            <form className="chat-footer" onSubmit={handleSendMessage}>
              <input
                type="text"
                value={chatInput}
                onChange={e => setChatInput(e.target.value)}
                placeholder="Ask about Kohli's stats or win probability..."
                disabled={isChatLoading}
              />
              <button type="submit" className="btn btn-primary" disabled={isChatLoading || !chatInput.trim()}>
                Send
              </button>
            </form>
          </aside>
        </div>
      )}

      {/* Create Match Modal */}
      {showCreateModal && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h3>Configure Match Simulation</h3>
            <form onSubmit={handleCreateMatch}>
              <div className="form-group">
                <label>Select Team 1</label>
                <select value={team1} onChange={e => setTeam1(e.target.value)}>
                  {teams.map(t => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>Select Team 2</label>
                <select value={team2} onChange={e => setTeam2(e.target.value)}>
                  {teams.map(t => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>

              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Start Match
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

export default App
