'''
Created on Aug 5, 2014

@author: Ronald Haentjens Dekker
'''
from sets import Set

class EditGraphNode(object):
    def __init__(self):
        self.g = 0
        self.parent = None
        
    def __repr__(self):
        return repr(self.g)

class EditGraphAligner(object):
    '''
    Aligner based on an edit graph.
    It needs a g function and a definition of a match.
    Since every node of the graph has three children the graph is represented as a table internally.
    THIS CLASS IS ONLY HERE TO SHOW HOW THE SCORING WORKS AND THAT EVERY POSSIBLE COMBINATION IS CONSIDERED!
    THE A* BASED ALIGNER WILL BE MUCH FASTER!
    '''
    def __init__(self, witness_a, witness_b):
        '''
        Constructor
        '''
        self.tokens_witness_a = witness_a.tokens()
        self.tokens_witness_b = witness_b.tokens()
        self.length_witness_a = len(self.tokens_witness_a)
        self.length_witness_b = len(self.tokens_witness_b)
        self.table = [[EditGraphNode() for _ in range(self.length_witness_a)] for _ in range(self.length_witness_b)]
        
    # This function traverses the table diagonally and scores each cell.
    # Original function from Mark Byers; translated from C into Python.
    def traverse_diagonally(self):
        m = self.length_witness_b
        n = self.length_witness_a
        for _slice in range(0, m + n - 1, 1):
            z1 = 0 if _slice < n else _slice - n + 1;
            z2 = 0 if _slice < m else _slice - m + 1;
            j = _slice - z2
            while j >= z1:
                self.table[j][_slice-j].g=self.score(j, _slice - j)
                j -= 1
    
    def align(self):
        # per diagonal calculate the score (taking into account the three surrounding nodes)
        self.traverse_diagonally()
        
            
    def score(self, y, x):
        # now we need to determine whether this node represents a match
        token_a = self.tokens_witness_a[x]
        token_b = self.tokens_witness_b[y]
        match = token_a.token_string == token_b.token_string
        # initialize root node score to zero if match, -1 if non match
        if y == 0 and x == 0:
            return 0 if match else -1
        nodes_to_examine = Set()
        # fetch existing score from the left node if possible
        if x > 0:
            nodes_to_examine.add(self.table[y][x-1])
        if y > 0:
            nodes_to_examine.add(self.table[y-1][x])
        if x > 0 and y > 0:
            nodes_to_examine.add(self.table[y-1][x-1])
        # calculate the maximum scoring parent node
        parent_node = max(nodes_to_examine, key=lambda x: x.g)
        # based on match or not and parent_node calculate new score
        if match:
            # do not change score for now 
            # TODO: this should do +1 or +2 depending on already in a match or not
            score = parent_node.g
        else:
            # TODO: this should do -1 or -2 depending on already in a match or not
            score = parent_node.g - 1
        return score